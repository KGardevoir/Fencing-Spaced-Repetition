@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// Platform-independent core of the app: the scheduling algorithms and the
// settings constants they read. Everything here compiles against the Kotlin
// standard library and nothing else, which is what makes the browser port
// possible.
//
// The jvm() target is what the Android app consumes -- it takes the plain jar,
// which keeps this module out of the Android Gradle plugin's way. The wasmJs()
// target is the browser core. It carries no production code of its own yet;
// its job right now is to run commonTest in a real browser engine, so we know
// FSRS and SM-2 schedule identically there before any UI is ported.
kotlin {
    jvmToolchain(17)

    jvm()

    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
