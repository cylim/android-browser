plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// GeckoView ships one AAR per ABI. x86_64 runs on the emulators (locally and in CI); build for a
// phone or tablet with -PgeckoAbi=arm64-v8a.
val geckoAbi = (findProperty("geckoAbi") as String?) ?: "x86_64"

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
