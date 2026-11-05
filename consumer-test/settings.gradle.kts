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
        // The libraries under test come from Maven Local and nowhere else. exclusiveContent matters: a plain
        // content filter would still let Maven Central serve this group, so a publish that silently failed
        // would be hidden by the released artifact, and the build would test the wrong thing.
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeGroup("io.github.ammaralghifary") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "consumer-test"
include(":app")
