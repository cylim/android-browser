plugins {
    id("com.android.application")
}

android {
    // Deliberately different from the real app's ID (app.jrny.browser) so the two never collide.
    namespace = "app.jrny.spike.signin"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.jrny.spike.signin"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1-spike"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.18.0")
}
