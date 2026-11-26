// A tiny consumer of the published libraries, built on its own (it is not part of the main build).
// What it is built with is a parameter, so one project covers a matrix:
//   -Pkotlin=2.3.21   Kotlin compiler and plugins      (default 2.3.21)
//   -Pagp=9.3.1       Android Gradle Plugin            (default 9.3.1)
// See README.md.
buildscript {
    val kotlinVersion = (project.findProperty("kotlin") ?: "2.3.21") as String
    val agpVersion = (project.findProperty("agp") ?: "9.3.1") as String
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:$agpVersion")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
    }
}
