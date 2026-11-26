import com.vanniktech.maven.publish.MavenPublishBaseExtension
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
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
    // Also pins the ABI of :core's non-JVM (iOS) targets, in core/api/core.klib.api.
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
        // Fail instead of silently skipping when a host cannot build one of the targets.
        strictValidation = true
    }
}

// The validator only finds Android classes through the pre-AGP 9 Android Kotlin plugin and the old KMP
// Android target, so on its own it would pass without looking at either library's Android API. These wire its
// own tasks to the compiled classes and hang them on apiCheck and apiDump.
fun Project.validateAndroidApi(compileTask: String, dumpPath: String) {
    // The validator adds this itself only when it sees a Kotlin plugin id, which built-in Kotlin does not use.
    dependencies.add("bcv-rt-jvm-cp", "org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
    val build = tasks.register<KotlinApiBuildTask>("androidReleaseApiBuild") {
        inputClassesDirs.from(tasks.named<KotlinJvmCompile>(compileTask).flatMap { it.destinationDirectory })
        outputApiFile.set(layout.buildDirectory.file("androidApi/${project.name}.api"))
        runtimeClasspath.from(configurations.named("bcv-rt-jvm-cp-resolver"))
    }
    val dumpFile = layout.projectDirectory.file(dumpPath)
    val check = tasks.register<KotlinApiCompareTask>("androidReleaseApiCheck") {
        group = "verification"
        description = "Checks the public Android API against $dumpPath"
        projectApiFile.set(dumpFile)
        generatedApiFile.set(build.flatMap { it.outputApiFile })
    }
    val dump = tasks.register<Copy>("androidReleaseApiDump") {
        group = "other"
        description = "Writes the public Android API to $dumpPath"
        from(build.flatMap { it.outputApiFile })
        into(dumpFile.asFile.parentFile)
    }
    // :core gets these two from the validator (for its desktop and iOS targets); :defaults gets none.
    listOf("apiCheck" to check, "apiDump" to dump).forEach { (name, task) ->
        if (name !in tasks.names) tasks.register(name)
        tasks.named(name) { dependsOn(task) }
    }
}

project(":core") {
    plugins.withId("com.android.kotlin.multiplatform.library") {
        // After evaluation, so the validator has registered its own apiCheck and apiDump first.
        afterEvaluate { validateAndroidApi("compileAndroidMain", "api/android/core.api") }
    }
}
project(":defaults") {
    plugins.withId("com.android.library") {
        afterEvaluate {
            validateAndroidApi("compileReleaseKotlin", "api/defaults.api")
            tasks.named("check") { dependsOn("apiCheck") }
        }
    }
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
