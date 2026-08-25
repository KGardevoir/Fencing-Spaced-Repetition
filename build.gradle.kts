// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Kotlin is 2.4.10 rather than 2.2.10 because klibs are readable forwards
// only, and Room 3.0.1's wasm klibs postdate 2.2.10 -- under it the frontend
// reported every androidx.room3 reference unresolved and the backend died
// loading their IR with "Built-in class kotlin.Any is not found". Nothing in
// the source tree fixes that; the compiler has to be new enough to read them.
//
// KSP moved to its own version line at 2.3.0, so its version no longer
// mirrors Kotlin's: 2.3.11 is the current release, not a Kotlin 2.3 pairing.
plugins {
    id("com.android.application") version "9.0.0" apply false
    id("com.android.library") version "9.0.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    // Compose Multiplatform. Distinct from the line above: that one is
    // Kotlin's Compose compiler plugin and moves with Kotlin, this one is the
    // multiplatform runtime and its own Gradle plugin.
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
}

// Print every test as it runs, in every module.
//
// Gradle says nothing about passing tests, which is tolerable for a JVM suite
// whose absence would be obvious, and not for a browser suite: a wasmJs test
// task that discovers nothing still reports success, so "BUILD SUCCESSFUL"
// alone cannot distinguish a passing browser test from one that never ran.
//
// Here rather than in each module because that distinction is not module
// specific, and because a new module would otherwise start out silent -- which
// is exactly what happened when :ui was added.
subprojects {
    tasks.withType<AbstractTestTask>().configureEach {
        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            showStackTraces = true
        }
    }
}
