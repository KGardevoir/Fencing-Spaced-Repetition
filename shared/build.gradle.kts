plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// Platform-independent core of the app: the scheduling algorithms and the
// settings constants they read. Everything here compiles against the Kotlin
// standard library and nothing else, which is what makes the browser port
// possible.
//
// Only the JVM target is declared for now. The Android app consumes the
// resulting jar directly, so this module stays out of the Android Gradle
// plugin's way entirely. Adding `wasmJs()` here is what turns this into the
// shared core for the web build; nothing in commonMain has to change for it.
kotlin {
    jvmToolchain(17)

    jvm()

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
