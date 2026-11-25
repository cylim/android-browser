pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // GeckoView, the browser engine (ADR 0006). Only Mozilla's own artifacts come from here.
        maven("https://maven.mozilla.org/maven2/") {
            content { includeGroup("org.mozilla.geckoview") }
        }
    }
}

rootProject.name = "jrny-browser"
include(":app")
