pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "immich-wallpaper"

// Pure-JVM modules build anywhere.
include(":core")
include(":data")
include(":render")
include(":presentation")

// Android modules need the Android SDK and Google Maven. Pass -Pimmichwall.jvmOnly=true
// to build only the JVM modules (e.g. in environments without the Android SDK).
if (providers.gradleProperty("immichwall.jvmOnly").orNull != "true") {
    include(":app")
    include(":render-android")
}
