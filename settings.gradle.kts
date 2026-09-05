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
        // Tesseract4Android (actively maintained fork of the abandoned com.rmtheis:tess-two)
        // is only published on JitPack.
        maven("https://jitpack.io")
    }
}

rootProject.name = "PDF Master"
include(":app")
