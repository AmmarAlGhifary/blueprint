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
        // The libraries under test come from Maven Local only, so a copy on Maven Central can never
        // satisfy this build by accident.
        mavenLocal {
            content { includeGroup("io.github.ammaralghifary") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "consumer-test"
include(":app")
