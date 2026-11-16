plugins {
    id("com.android.application")
}

// Release channel on purpose: release GeckoView enforces extension signing at build time,
// which is one of the things this spike checks. Latest release on 2026-11-16.
val geckoviewVersion = "160.0.20261104181552"
// x86_64 for the emulator. Build for a phone or tablet with -PgeckoAbi=arm64-v8a.
val geckoAbi = (findProperty("geckoAbi") as String?) ?: "x86_64"
// -PwithFido=true puts Google's play-services-fido back, to compare passkeys with and without it.
val withFido = (findProperty("withFido") as String?) == "true"

android {
    // Deliberately different from the real app's ID (app.jrny.browser) so the two never collide.
    namespace = "app.jrny.spike.gvext"
    // GeckoView 160 requires compileSdk 37.2.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.jrny.spike.gvext"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1-spike"
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-$geckoAbi:$geckoviewVersion") {
        // ADR 0002 check: GeckoView declares Google's proprietary FIDO library as a runtime
        // dependency. Leave it out by default and see what still works without it.
        if (!withFido) exclude(group = "com.google.android.gms", module = "play-services-fido")
    }
}
