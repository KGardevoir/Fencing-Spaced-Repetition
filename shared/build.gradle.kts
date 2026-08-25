@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import java.util.zip.ZipFile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

val roomVersion = "3.0.1"
val sqliteVersion = "2.7.0"
val browserVersion = "0.5.0"
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
//     so we know FSRS and SM-2 schedule identically there, and it now compiles
//     the whole data layer including the generated database. Opening one still
//     needs a driver: androidx.sqlite:sqlite-web and WebWorkerSQLiteDriver,
//     which is the next piece.
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
        // The worker is addressed as new URL(..., import.meta.url), which
        // webpack rewrites into a chunk reference. import.meta only exists in
        // an ES module, so the output format is not incidental here.
        useEsModules()

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

        val wasmJsMain by getting {
            // Worker and the other browser declarations are behind this
            // marker; androidx's own sqlite-web opts in the same way.
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")

            dependencies {
                // WebWorkerSQLiteDriver: a SQLiteDriver that does no SQLite
                // work itself, but posts messages to a Web Worker that does.
                implementation("androidx.sqlite:sqlite-web:$sqliteVersion")

                // org.w3c.dom.Worker. The browser declarations left the
                // Kotlin standard library, so on wasmJs they are a dependency
                // rather than something already on the classpath.
                implementation("org.jetbrains.kotlinx:kotlinx-browser:$browserVersion")

                // The worker itself, vendored, because androidx publishes the
                // driver without one -- see third_party/sqlite-web-worker.
                // A directory rather than a registry coordinate: the file is
                // in this repository, and webpack pulls it out of node_modules
                // once yarn has linked it there.
                implementation(
                    npm(
                        "fencing-sqlite-web-worker",
                        rootProject.layout.projectDirectory
                            .dir("third_party/sqlite-web-worker").asFile
                    )
                )
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val wasmJsTest by getting {
            dependencies {
                // runTest, so the browser test can await the worker.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
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
// Room's compiler runs for every target now, because the @Database moved to
// commonMain and Room generates a separate implementation per platform. That
// is what @ConstructedBy is for: it names the constructor so Room does not
// have to find the implementation reflectively, which it cannot do off the
// JVM.
//
// Which brings back the problem that made this Android-only. room.schemaLocation
// names one directory for the whole module, and several KSP tasks writing it
// at once produced a half-written file:
//
//     e: [ksp] JsonDecodingException: Unexpected JSON token at offset 13236
//        ... at path: $.database.entities[4]
//
// passing on one run and failing on the next. Room's own Gradle plugin keeps
// the directories apart and is the documented answer, but its extension did
// not resolve under this build's plugin set and a second guess at the name is
// not worth a round trip.
//
// So the tasks are ordered instead, below. The schema describes the database,
// not the platform, so every target generates identical bytes -- ordering them
// means they overwrite each other with the same content rather than
// interleaving. That is a weaker guarantee than separate directories and it is
// written down as such: if the targets ever diverge, this needs the plugin.
val kspTargetNames = listOf("android", "jvm", "wasmJs")
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
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The ordering the note above describes. Chained rather than fanned out, so
// no two KSP tasks can be writing the schema directory at the same time.
afterEvaluate {
    val kspTaskNames = tasks.names
        .filter { it.startsWith("ksp") && it.contains("Kotlin") }
        .sorted()
    logger.lifecycle("KSPCFG: serialising ksp tasks = $kspTaskNames")
    kspTaskNames.zipWithNext().forEach { (earlier, later) ->
        tasks.named(later) { mustRunAfter(tasks.named(earlier)) }
    }
}

// TEMPORARY, paired with the CI step of the same name. androidx publishes no
// reference documentation for androidx.sqlite:sqlite-web, so this prints what
// the klib actually contains: the packages it declares and the names inside
// them. That is the difference between knowing the driver's import and
// guessing it, and it costs one task rather than one CI round trip per guess.
// Delete once the browser database test is green.
tasks.register("dumpSqliteWebApi") {
    description = "Prints the packages and top-level names in the sqlite-web klib."
    doLast {
        val klibs = configurations.getByName("wasmJsCompileClasspath")
            .incoming.artifactView { isLenient = true }.files
            .filter { it.name.contains("sqlite-web") }

        if (klibs.isEmpty()) {
            println("SQLITEWEB: nothing named sqlite-web on the wasmJs compile classpath")
        }

        klibs.forEach { klib ->
            println("SQLITEWEB: artifact ${klib.name}")
            // Imported at the top of the file rather than written
            // out here: in a Kotlin DSL build script 'java' is the
            // Java extension accessor, which shadows the package.
            ZipFile(klib).use { zip ->
                val entries = zip.entries().toList()

                entries.map { it.name }
                    .mapNotNull { Regex("package_([\\w.]+)").find(it)?.groupValues?.get(1) }
                    .distinct().sorted()
                    .forEach { println("SQLITEWEB: package $it") }

                // Declaration names survive in the metadata as plain strings,
                // which is enough to tell a constructor's shape from a guess.
                val names = entries
                    .filter { it.name.endsWith(".knm") }
                    .flatMap { entry ->
                        val text = zip.getInputStream(entry).readBytes()
                            .toString(Charsets.ISO_8859_1)
                        Regex("[A-Za-z][A-Za-z0-9_.]{3,}").findAll(text).map { it.value }
                    }
                    .filter { it.contains("Worker") || it.contains("Driver") }
                    .distinct().sorted()
                names.forEach { println("SQLITEWEB: name $it") }
            }
        }
    }
}
