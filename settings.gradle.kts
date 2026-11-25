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
    }
}

rootProject.name = "ImageEnhancerPro"
include(":app")
// AI models (~170 MB) ship as an install-time asset pack, downloaded together with the app from Play.
include(":models")
