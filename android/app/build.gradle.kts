import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// GeckoView ships one AAR per ABI. x86_64 runs on the emulators (locally and in CI); build for a
// phone or tablet with -PgeckoAbi=arm64-v8a.
val geckoAbi = (findProperty("geckoAbi") as String?) ?: "x86_64"

// uBlock Origin, built in as the content blocker (ADR 0008): the signed Firefox build from its
// GitHub release, checked against this SHA-256 and put into the APK's assets unchanged. To update,
// change both lines: `sha256sum uBlock0_<version>.firefox.signed.xpi`.
val ublockVersion = "1.77.2"
val ublockSha256 = "c9186bcbb5ef542465187ce52aa7d76b5315e8a020c360e3e701f678ad47d776"

/** Downloads uBlock Origin's release and refuses it unless its hash matches. */
abstract class FetchUblock : DefaultTask() {
    @get:Input abstract val version: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val files: FileSystemOperations

    @TaskAction
    fun fetch() {
        val name = "uBlock0_${version.get()}.firefox.signed.xpi"
        val xpi = temporaryDir.resolve(name)
        if (!xpi.exists() || hashOf(xpi) != sha256.get()) {
            URI("https://github.com/gorhill/uBlock/releases/download/${version.get()}/$name").toURL()
                .openStream().use { input -> xpi.outputStream().use { input.copyTo(it) } }
        }
        val got = hashOf(xpi)
        check(got == sha256.get()) { "$name has SHA-256 $got, not ${sha256.get()}" }
        // As published, so Mozilla's signature still covers it: GeckoView installs only signed extensions.
        files.sync {
            from(xpi) { rename { "uBlock0@raymondhill.net.xpi" } }
            into(outputDir.dir("extensions"))
        }
    }

    private fun hashOf(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}

val fetchUblock = tasks.register<FetchUblock>("fetchUblock") {
    version = ublockVersion
    sha256 = ublockSha256
    outputDir = layout.buildDirectory.dir("generated/ublock")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(fetchUblock, FetchUblock::outputDir)
    }
}

android {
    namespace = "app.jrny.browser"
    // GeckoView's AAR metadata asks for compileSdk 37.2.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.jrny.browser"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Room writes each database version's schema here, for migrations and their tests.
    sourceSets["androidTest"].assets.directories.add("$projectDir/schemas")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("org.mozilla.geckoview:geckoview-$geckoAbi:${libs.versions.geckoview.get()}") {
        // ADR 0002: GeckoView declares Google's proprietary FIDO library for passkeys. Without it,
        // passkeys go through Android's Credential Manager only, which needs no Google library.
        exclude(group = "com.google.android.gms", module = "play-services-fido")
    }
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.guava)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    // Backup files (SPEC, "Backup").
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
