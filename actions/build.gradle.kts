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
        namespace = "io.github.ammaralghifary.blueprint.actions"
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
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
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
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        // Tests that need a JVM: the schema is read from a file.
        val jvmSharedTest = create("jvmSharedTest") {
            dependsOn(commonTest.get())
            dependencies { implementation(libs.junit) }
        }
        getByName("desktopTest").dependsOn(jvmSharedTest)

        getByName("androidHostTest") {
            dependsOn(jvmSharedTest)
            dependencies {
                // Only the tests: they drive the stock button through a controller. :actions itself and
                // :defaults do not depend on each other.
                implementation(project(":defaults"))
                implementation(libs.compose.multiplatform.foundation)
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
        name.set("Blueprint Actions")
        description.set("Optional actions for Blueprint: open_url, submit and set_state, a controller that tracks each action from Running to Done or Failed, and the state set_state writes. The host app handles every action.")
    }
}
