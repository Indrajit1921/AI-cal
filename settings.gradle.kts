import org.gradle.api.initialization.resolve.RepositoriesMode.*

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
    with(repositoriesMode) { repositoriesMode.set(FAIL_ON_PROJECT_REPOS) }
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Demo"
include(":app")
