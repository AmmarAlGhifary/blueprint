import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// -Pcompose=2024.09.00 is the Compose BOM the consumer uses (default: the oldest the library supports).
val composeBom = providers.gradleProperty("compose").getOrElse("2024.09.00")

// -Planguage=2.0 pins languageVersion and apiVersion, as many apps do. Without it the consumer compiles at
// its Kotlin version's own default, which is a different (and less common) situation.
val languageLevel = providers.gradleProperty("language").orNull

// The version just published from this checkout; -Pblueprint=... overrides it.
val blueprintVersion: String = providers.gradleProperty("blueprint").orNull
    ?: Properties().apply { rootDir.parentFile.resolve("gradle.properties").inputStream().use(::load) }
        .getProperty("VERSION_NAME")

android {
    namespace = "com.example.consumer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.consumer"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        languageLevel?.let {
            languageVersion.set(KotlinVersion.fromVersion(it))
            apiVersion.set(KotlinVersion.fromVersion(it))
        }
    }
}

// Only what a real app would declare. kotlinx-serialization is deliberately NOT listed: the library
// exposes it as an api dependency, so if its POM stops doing that, this project stops compiling.
dependencies {
    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("io.github.ammaralghifary:blueprint-core:$blueprintVersion")
    implementation("io.github.ammaralghifary:blueprint-defaults:$blueprintVersion")
}
