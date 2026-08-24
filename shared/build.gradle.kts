@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// Platform-independent core of the app: the scheduling algorithms and the
// settings constants they read. Everything here compiles against the Kotlin
// standard library and nothing else, which is what makes the browser port
// possible.
//
// Three targets:
//
//   androidLibrary -- what the Android app consumes. This is the AGP 9 KMP
//     library plugin (com.android.kotlin.multiplatform.library) rather than
//     the classic com.android.library + androidTarget() pairing; AGP 9 is the
//     version where the new plugin is the supported way to put an Android
//     target in a multiplatform module. The data layer needs this target,
//     because Room generates a separate implementation per platform and the
//     Android one has to be compiled against the Android variant of the Room
//     runtime.
//
//   jvm -- the fast path for running commonTest. It is not consumed by any
//     other module; it exists so `:shared:jvmTest` can check the shared code
//     in a second or two, without an emulator and without a browser.
//
//   wasmJs -- the browser core. It runs commonTest in a real browser engine,
//     so we know FSRS and SM-2 schedule identically there.
kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "com.fencing.spacedrepetition.shared"
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

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
        val commonMain by getting {
            dependencies {
                // Room's annotations and runtime, for the entities and the
                // converters. Room 3 is the first release with a wasmJs
                // target, which is the whole reason the data layer can be
                // here at all.
                //
                // api rather than implementation: these types appear in the
                // entities' own declarations, so anything consuming :shared
                // has to see them.
                api("androidx.room3:room3-runtime:3.0.1")
            }
        }
        // The Android and JVM targets share an implementation for everything
        // that only needs the Java standard library -- the UTC offset lookup
        // today, and the JVM half of the data layer's file and gzip I/O later.
        // Without this set the two would hold byte-identical copies of every
        // actual declaration, which is the kind of duplication that silently
        // drifts.
        val jvmCommonMain by creating {
            dependsOn(commonMain)
        }
        val jvmMain by getting {
            dependsOn(jvmCommonMain)
        }
        val androidMain by getting {
            dependsOn(jvmCommonMain)
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
