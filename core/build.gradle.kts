import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    // First, so the root script sets the Maven coordinates before the publish plugin reads (and freezes)
    // them, which it does as soon as it sees an already-applied multiplatform plugin.
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()
    // The published POMs depend on this stdlib, not on the compiler's own (see libs.versions.toml).
    coreLibrariesVersion = libs.versions.kotlinStdlibFloor.get()
    compilerOptions {
        // Keep the published metadata readable by consumers on the previous Kotlin minor.
        val floor = KotlinVersion.fromVersion(libs.versions.kotlinLanguage.get())
        languageVersion.set(floor)
        apiVersion.set(floor)
    }

    android {
        namespace = "io.github.ammaralghifary.blueprint"
        compileSdk = 36
        minSdk = 23

        // AGP 9 would otherwise require this module's own compileSdk of every app. 35 is what Compose needs.
        aarMetadata {
            minCompileSdk = 35
        }

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }

        withHostTest {
            isIncludeAndroidResources = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            // The module name apps have seen since 0.1.0; it is part of generated JVM names.
            moduleName.set("core_release")
            // SduiScope's default methods stay in DefaultImpls, the shape apps were compiled against.
            jvmDefault.set(JvmDefaultMode.DISABLE)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            jvmDefault.set(JvmDefaultMode.DISABLE)
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        // languageVersion does not cover native libraries: without this the klibs carry the compiler's own
        // ABI version, which the previous Kotlin minor cannot read. 2.4 can write one minor back, no further.
        target.compilerOptions {
            freeCompilerArgs.add("-Xklib-abi-version=${libs.versions.kotlinLanguage.get()}.0")
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.multiplatform.runtime)
            api(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.foundation)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // What Android and desktop share because both run on a JVM.
        val jvmSharedMain = create("jvmSharedMain") { dependsOn(commonMain.get()) }
        val jvmSharedTest = create("jvmSharedTest") {
            dependsOn(commonTest.get())
            dependencies { implementation(libs.junit) }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        getByName("desktopMain").dependsOn(jvmSharedMain)
        getByName("desktopTest").dependsOn(jvmSharedTest)

        getByName("androidHostTest") {
            dependsOn(jvmSharedTest)
            dependencies {
                // Library is compiled against the oldest supported Compose; render tests run on a newer one.
                implementation(project.dependencies.platform(libs.androidx.compose.bom.latest))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.androidx.compose.ui.test.manifest)
                implementation(libs.androidx.test.core)
                implementation(libs.robolectric)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Blueprint Core")
        description.set("Server-driven UI for Compose Multiplatform (Android, desktop, iOS): decodes JSON payloads into a component tree and renders it through a host-provided registry. Decoding is tolerant, so one bad node never takes down a screen.")
    }
}
