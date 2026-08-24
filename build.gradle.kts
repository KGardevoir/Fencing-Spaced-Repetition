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
    id("com.google.devtools.ksp") version "2.3.11" apply false
}
