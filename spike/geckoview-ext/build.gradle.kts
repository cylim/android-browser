// GeckoView 160 is built with Kotlin 2.4, newer than the Kotlin bundled with AGP 9.5,
// so pin the Kotlin Gradle plugin AGP's built-in Kotlin support uses.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
}
