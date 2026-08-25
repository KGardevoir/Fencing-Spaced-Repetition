@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// The user interface, once. :shared holds the data and the scheduling; this
// holds everything drawn on top of it, for both the Android app and the
// browser.
//
// A module of its own rather than more source sets in :shared, for two
// reasons. :shared has a jvm target that exists purely so the pure-logic
// tests run in a second without an emulator or a browser, and pulling Compose
// into it would make that target carry a UI toolkit it never draws with.
// And Compose is the one dependency here that is genuinely large: keeping it
// behind a module boundary means the data layer can still be consumed without
// it.
//
// No jvm target, therefore, and no KSP: nothing here is annotated.
kotlin {
    jvmToolchain(17)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    wasmJs {
        // Matches :shared and :web. The whole wasm build has to agree on the
        // module format, and the SQLite worker URL needs import.meta.
        useEsModules()
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // api rather than implementation: a screen takes a Card or a
                // Group in its signature, so anything consuming :ui sees
                // :shared's types anyway.
                api(project(":shared"))

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }

        val androidMain by getting {
            dependencies {
                // WindowCompat, for the status bar colour the Android theme
                // sets. There is no browser equivalent -- see the actual in
                // wasmJsMain.
                implementation("androidx.core:core-ktx:1.12.0")
            }
        }
    }
}

android {
    namespace = "com.fencing.spacedrepetition.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
