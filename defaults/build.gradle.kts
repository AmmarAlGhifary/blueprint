import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

android {
    namespace = "io.github.ammaralghifary.blueprint.defaults"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        // AGP 9 would otherwise require this module's own compileSdk of every app. 35 is what Compose needs.
        aarMetadata {
            minCompileSdk = 35
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    explicitApi()
    // The published POM depends on this stdlib, not on the compiler's own (see libs.versions.toml).
    coreLibrariesVersion = libs.versions.kotlinStdlibFloor.get()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        jvmDefault.set(JvmDefaultMode.DISABLE)
        // Keep the published metadata readable by consumers on the previous Kotlin minor.
        val floor = KotlinVersion.fromVersion(libs.versions.kotlinLanguage.get())
        languageVersion.set(floor)
        apiVersion.set(floor)
    }
}

// The module name apps have seen since 0.1.0; it is part of generated JVM names, and AGP 9 dropped the suffix.
tasks.withType<KotlinJvmCompile>().matching { it.name == "compileReleaseKotlin" }.configureEach {
    compilerOptions.moduleName.set("defaults_release")
}

dependencies {
    api(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom.latest))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

mavenPublishing {
    pom {
        name.set("Blueprint Defaults")
        description.set("Optional stock component pack for Blueprint (text, image, button, card, column, row, box, list, spacer, divider) built on Material 3 and Coil, so a host app can render a screen without writing any components.")
    }
}
