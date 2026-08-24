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
//     so we know FSRS and SM-2 schedule identically there. It does not yet
//     compile the data layer; see the note above the KSP block at the bottom
//     for what is blocking that and what has been ruled out.
kotlin {
    jvmToolchain(17)

    // Applied explicitly because jvmCommonMain below declares a dependsOn edge
    // by hand, and Kotlin disables the default hierarchy template as soon as it
    // sees one -- warning "Default Kotlin Hierarchy Template Not Applied
    // Correctly" and leaving the target source sets with no edge to commonMain.
    // That is what put androidx.room3 off the wasmJs compile classpath.
    // Calling it explicitly keeps the template and the manual edge together.
    applyDefaultHierarchyTemplate()

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
                // Flow, for SchedulingPreferences and the DAOs. Pinned rather
                // than left to a transitive choice so that :app's
                // coroutines-android artifact cannot end up on a different
                // version from the core it is meant to pair with.
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

                // Room's annotations and runtime, for the entities, DAOs and
                // converters that now live here. api rather than
                // implementation: Room's types appear in the entities' own
                // declarations, so anything consuming :shared has to see them.
                api("androidx.room3:room3-runtime:$roomVersion")

                // SQLiteConnection and execSQL, which the migrations call
                // directly. Room brings it transitively; the migrations name
                // it, so it is declared.
                api("androidx.sqlite:sqlite:$sqliteVersion")

            }
        }
        // The Android and JVM targets share an implementation for everything
        // that only needs the Java standard library. That is one file today --
        // the UTC offset lookup -- and will be the JVM half of the data
        // layer's file and gzip I/O next. Without this set the two would hold
        // byte-identical copies of every actual declaration, which is the kind
        // of duplication that silently drifts.
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
//
// Only the Android target generates a database, so only kspAndroid is wired
// up. Two reasons, and both matter.
//
// The database is Android-only, and now that is the only part of the data
// layer that is. Kotlin 2.4.10 reads Room's wasm klibs, so the entities,
// converters, DAOs and repositories compile for the browser and live in
// commonMain again.
//
// What is left for step 6 is the @Database itself. Putting it in commonMain
// needs @ConstructedBy and a generated actual per target, which means
// kspWasmJs and kspJvm alongside kspAndroid -- and that reintroduces the
// several-generators-one-schema-directory race described below. It also wants
// androidx.sqlite:sqlite-web and WebWorkerSQLiteDriver, without which a
// browser database can be generated but not opened. Those belong together, in
// one change, not bolted on here.
//
// And one generator means one writer of the exported schema. room.schemaLocation
// is a single directory for the whole module, and when jvm generated a database
// too, the two ran in parallel into it and produced a half-written file:
//
//     e: [ksp] JsonDecodingException: Unexpected JSON token at offset 13236
//        ... at path: $.database.entities[4]
//
// which passed on one run and failed on the next. Room's own Gradle plugin is
// the documented answer to that and would allow several generators again, but
// its extension did not resolve under this build's plugin set, so the race is
// removed by construction instead: the jvm target compiles the DAOs and
// entities and never generates an implementation of them.
val kspTargetNames = listOf("android")
val kspConfigurationNames = kspTargetNames.map { target ->
    "ksp" + target.replaceFirstChar { it.uppercase() }
}
val missingKspConfigurations = kspConfigurationNames.filterNot { it in configurations.names }

logger.lifecycle("KSPCFG: targets = $kspTargetNames")
kotlin.sourceSets.filter { it.name.endsWith("Main") }.sortedBy { it.name }.forEach { sourceSet ->
    logger.lifecycle("KSPCFG: ${sourceSet.name} dependsOn ${sourceSet.dependsOn.map { it.name }.sorted()}")
}
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
//
// room.schemaLocation names one directory for the whole module, so it is only
// safe while exactly one compilation generates a database. That is now true by
// construction -- see the KSP block above -- and it is what the concurrent
// half-written schema failure was about.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Kept for step 6, when Room has to reach the browser. It prints the wasmJs
// compile classpath, which is currently:
//
//     kotlinx-coroutines-core-wasm-js, kotlin-stdlib-wasm-js, atomicfu-wasm-js
//
// and nothing else. That is the expected shape now that Room is declared in
// jvmCommonMain, so it no longer answers the original question -- whether
// Room's klib was absent or present-and-skipped when it was in commonMain --
// because the configuration changed before this diagnostic produced any
// readable output. Re-adding room3 to commonMain and running this task is the
// first move of that investigation.
tasks.register("dumpWasmJsCompileClasspath") {
    val classpath = kotlin.targets.getByName("wasmJs")
        .compilations.getByName("main")
        .compileDependencyFiles
    doLast {
        // println, not logger.lifecycle: the CI step runs Gradle with -q,
        // which suppresses the lifecycle log level. The first attempt at this
        // diagnostic used lifecycle and printed nothing at all.
        classpath.forEach { println("WASMCP: ${it.name}") }
    }
}
