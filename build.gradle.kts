plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.binary.compatibility.validator)
}

// Self-hosted SonarQube. The token is never committed: set SONAR_TOKEN (and SONAR_HOST_URL to override
// the local default) in the environment. Without a host the scanner assumes SonarCloud.
sonar {
    properties {
        property("sonar.host.url", providers.environmentVariable("SONAR_HOST_URL").getOrElse("http://localhost:9000"))
        property("sonar.projectKey", "blueprint")
        property("sonar.projectName", "Blueprint")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.exclusions", "**/build/**")
    }
}

// Android modules resolve no source sets here, so name them or the scanner falls back to the whole
// module directory and counts tests as production code.
subprojects {
    extensions.configure<org.sonarqube.gradle.SonarExtension> {
        properties {
            property("sonar.sources", "src/main")
            property("sonar.tests", "src/test")
        }
    }
}

// The sample app is not a library, so its API is not tracked. The dumps do pin Compose's generated
// ComposableSingletons classes: the hash in their `getLambda$<n>` names changes when the declaring file
// moves package, which is not an API change (editing the lambda body does not change it).
apiValidation {
    ignoredProjects.add("app")
}
