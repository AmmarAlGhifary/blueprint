import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
}

// The sample app is not a library, so its API is not tracked. The dumps do pin Compose's generated
// ComposableSingletons classes: the hash in their `getLambda$<n>` names changes when the declaring file
// moves package, which is not an API change (editing the lambda body does not change it).
apiValidation {
    ignoredProjects.add("app")
}

// Maven Central coordinates and POM, shared by the two published libraries (:core, :defaults). Each module
// adds its own name and description. The sample app does not apply the plugin, so it is not published.
//
// Credentials and signing keys come only from the developer's own ~/.gradle/gradle.properties (or CI
// secrets exposed as ORG_GRADLE_PROJECT_* variables): mavenCentralUsername, mavenCentralPassword,
// signingInMemoryKey, signingInMemoryKeyId and signingInMemoryKeyPassword. Nothing secret is in this repo.
subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            // Uploads to the Central Portal; the release is confirmed by hand in the portal, not automatic.
            publishToMavenCentral()
            // Sign only when a key is configured, so CI and other developers build without one.
            if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                signAllPublications()
            }
            coordinates("io.github.ammaralghifary", "blueprint-${project.name}", providers.gradleProperty("VERSION_NAME").get())
            pom {
                url.set("https://github.com/AmmarAlGhifary/blueprint")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("AmmarAlGhifary")
                        name.set("Ammar Al Ghifary")
                        url.set("https://github.com/AmmarAlGhifary")
                    }
                }
                scm {
                    url.set("https://github.com/AmmarAlGhifary/blueprint")
                    connection.set("scm:git:git://github.com/AmmarAlGhifary/blueprint.git")
                    developerConnection.set("scm:git:ssh://git@github.com/AmmarAlGhifary/blueprint.git")
                }
            }
        }
    }
}
