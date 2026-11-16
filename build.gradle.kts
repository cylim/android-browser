buildscript {
    dependencies {
        // Pins the Kotlin version used by AGP's built-in Kotlin support (see libs.versions.toml).
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
