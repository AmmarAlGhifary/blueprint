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
        namespace = "io.github.ammaralghifary.blueprint.defaults"
        compileSdk = 36
        minSdk = 23

        // AGP 9 would otherwise require this module's own compileSdk of every app. 35 is what Compose needs.
        aarMetadata {
            minCompileSdk = 35
        }

        withHostTest {
            isIncludeAndroidResources = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            // The module name apps have seen since 0.1.0; it is part of generated JVM names.
            moduleName.set("defaults_release")
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
        // As in :core: without this the klibs carry an ABI version the previous Kotlin minor cannot read.
        target.compilerOptions {
            freeCompilerArgs.add("-Xklib-abi-version=${libs.versions.kotlinLanguage.get()}.0")
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.coil.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // Coil loads nothing over the network by itself: each platform adds a fetcher, which Coil finds on
        // its own. OkHttp on the JVM targets (what Coil 2 used on Android), Ktor with NSURLSession on iOS.
        androidMain.dependencies {
            implementation(libs.coil.network.okhttp)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.coil.network.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.darwin)
        }

        // Tests that need a JVM: fixtures and the schema are read from files.
        val jvmSharedTest = create("jvmSharedTest") {
            dependsOn(commonTest.get())
            dependencies { implementation(libs.junit) }
        }
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
        name.set("Blueprint Defaults")
        description.set("Optional stock component pack for Blueprint (text, image, button, card, column, row, box, list, spacer, divider) built on Compose Multiplatform Material 3 and Coil, so an Android, desktop or iOS app can render a screen without writing any components.")
    }
}
