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
    // kotlinx.serialization's compiler plugin, which generates the encoders
    // for the export document's classes. Versioned with Kotlin, like the
    // Compose compiler plugin above -- it is part of the compiler, not a
    // library, so the two move together.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
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

// The commit this build came from, resolved once for the whole build.
//
// It used to live in :app, which was fine while :app was the only module that
// showed a version. :web shows the same About section now, and asking git the
// same question twice per build -- in two places free to answer it
// differently -- is worse than answering it here and handing the result down.
//
// providers.exec rather than a ProcessBuilder: it keeps stderr out of the
// value, and it is the form the configuration cache accepts. The version this
// replaced merged the two streams, so a build outside a git checkout reported
// "fatal: not a git repository" as its commit.
//
// "unknown" for anything that is not a checkout -- a released source archive,
// a machine without git. The About section says so rather than inventing a
// commit, which is the whole point of showing one.
val gitCommit: String = try {
    val git = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        workingDir = rootDir
        isIgnoreExitValue = true
    }
    if (git.result.get().exitValue == 0) {
        git.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() } ?: "unknown"
    } else {
        "unknown"
    }
} catch (e: Exception) {
    "unknown"
}
extra["gitCommit"] = gitCommit
