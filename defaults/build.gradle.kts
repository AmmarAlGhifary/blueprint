import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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
    // The published POM depends on this stdlib, not on the 2.2.10 compiler's own (see libs.versions.toml).
    coreLibrariesVersion = libs.versions.kotlinStdlibFloor.get()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // Keep the published metadata readable by consumers on older Kotlin compilers.
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
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
