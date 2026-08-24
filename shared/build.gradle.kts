@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

val roomVersion = "3.0.1"
val sqliteVersion = "2.7.0"
val coroutinesVersion = "1.10.2"

// Platform-independent core of the app: the scheduling algorithms and the
// settings constants they read. Everything here compiles against the Kotlin
// standard library and nothing else, which is what makes the browser port
// possible.
//
// Three targets:
//
//   androidTarget -- what the Android app consumes. The data layer needs this
//     target, because Room generates a separate implementation per platform
//     and the Android one has to be compiled against the Android variant of
//     the Room runtime.
//
//     This uses com.android.library + androidTarget() rather than AGP 9's
//     newer com.android.kotlin.multiplatform.library, and not by preference:
//     KSP does not support the new plugin (google/ksp#2476). With it applied,
//     KSP creates configurations for jvm and wasmJs and silently none for
//     android, and routing through the deprecated bare `ksp` configuration
//     instead throws KotlinMultiplatformAndroidCompilationImpl cannot be cast
//     to KotlinJvmAndroidCompilation. No Room codegen for Android is possible
//     that way, so the classic pairing it is, until KSP catches up.
//
//     AGP 9 refuses this pairing by default and names the bypass in its own
//     error message: android.builtInKotlin=false and android.newDsl=false,
//     both set in gradle.properties. Not android.enableLegacyVariantApi,
//     which the migration notes point at -- that property was removed in 9.0
//     and setting it fails the build.
//
//   jvm -- the fast path for running commonTest. It is not consumed by any
//     other module; it exists so `:shared:jvmTest` can check the shared code
//     in a second or two, without an emulator and without a browser.
//
//   wasmJs -- the browser core. It runs commonTest in a real browser engine,
//     so we know FSRS and SM-2 schedule identically there.
kotlin {
    jvmToolchain(17)

    androidTarget {
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
                api("androidx.room3:room3-runtime:$roomVersion")

                // SQLiteConnection and execSQL, which the migrations call
                // directly. Room brings this transitively, but the migrations
                // name it, so it is declared.
                api("androidx.sqlite:sqlite:$sqliteVersion")

                // The DAOs return Flow. Pinned rather than left to Room's
                // transitive choice so that :app's coroutines-android
                // artifact cannot end up on a different version from the
                // core it is meant to pair with.
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
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
            dependencies {
                // AndroidSQLiteDriver, used by the Android database builder.
                implementation("androidx.sqlite:sqlite-framework:$sqliteVersion")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

android {
    namespace = "com.fencing.spacedrepetition.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Room's compiler has to run once per target: the generated database
// implementation is platform-specific, which is the whole reason :shared
// needed an Android target before the data layer could move here.
//
// Derived from the target names rather than written out, and checked. A
// missing configuration is exactly the failure mode that cost a round trip
// here: KSP quietly created none for the Android target under the new AGP
// plugin, which would have meant no generated database on Android and a
// confusing error much later. Better to say so during configuration.
//
// The bare `ksp` and `kspCommonMainMetadata` configurations are deliberately
// not used. Room's codegen is per-platform, and the bare one is the path that
// threw the ClassCastException.
val kspTargetNames = kotlin.targets.map { it.name }.filter { it != "metadata" }
val kspConfigurationNames = kspTargetNames.map { target ->
    "ksp" + target.replaceFirstChar { it.uppercase() }
}
val missingKspConfigurations = kspConfigurationNames.filterNot { it in configurations.names }

logger.lifecycle("KSPCFG: targets = $kspTargetNames")
logger.lifecycle("KSPCFG: ksp configurations = ${configurations.names.filter { it.startsWith("ksp") }}")

if (missingKspConfigurations.isNotEmpty()) {
    error(
        "KSPCFG: no KSP configuration for $missingKspConfigurations. Room's " +
            "database implementation is generated per target, so a target " +
            "without one produces no database at all. Present: " +
            configurations.names.filter { it.startsWith("ksp") }
    )
}

dependencies {
    kspConfigurationNames.forEach {
        add(it, "androidx.room3:room3-compiler:$roomVersion")
    }
}

// Room writes a JSON description of the schema for each version here. It is
// the only record of what the database is supposed to look like, so it is
// committed rather than generated-and-discarded: without it a migration can
// only be checked by running the app.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
