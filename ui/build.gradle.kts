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
//
// The tests run in a real browser engine for the same reason :shared's do:
// on wasm, "it compiles" and "it renders" are separate claims, and only one
// of them is worth having. They are browser-only -- see the note on the test
// source set below.
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
                // api rather than implementation: a screen takes a Card or a
                // Group in its signature, so anything consuming :ui sees
                // :shared's types anyway.
                api(project(":shared"))

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)

                // BackHandler, which the group editor uses to guard unsaved
                // changes. Declared rather than relied on: material3 lists it
                // among its dependencies, but not as an api one, so it reaches
                // the runtime classpath and never the compile classpath.
                implementation("org.jetbrains.compose.ui:ui-backhandler:1.11.1")

                // Material's icons. Declared explicitly because material3
                // does not depend on them, and pinned to 1.7.3 because that
                // is the last version JetBrains published -- the icons
                // artifacts were discontinued after 1.7. They are fixed
                // vector paths that have not changed in years, so an old
                // artifact is a fair trade against hand-copying path data.
                //
                // The extended set is here because this app's screens use 40
                // icons outside the core 40-odd, and it does ship a wasmJs
                // build despite the deprecation. The alternative was writing
                // out 40 ImageVectors by hand. Nothing is bundled that is not
                // referenced: each icon is its own lazily-built property, so
                // dead-code elimination keeps only the ones a screen names.
                implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

                // ViewModel and viewModelScope, under the same
                // androidx.lifecycle package names the Android build already
                // uses -- which is why the view models move across unchanged.
                //
                // api rather than implementation: OpponentViewModel is public
                // and extends ViewModel, so anything calling a method on one
                // needs the supertype resolvable. As implementation it is not,
                // and the caller gets "Cannot access 'androidx.lifecycle.
                // ViewModel' which is a supertype of 'OpponentViewModel'".
                //
                // 2.9.x rather than the current 2.11.0, and the reason is not
                // caution: declaring 2.11.0 raises the whole androidx
                // lifecycle group to 2.11.0, including the artifacts Compose
                // Multiplatform pulls in itself, and
                // lifecycle-runtime-compose-android:2.11.0 requires Android
                // Gradle plugin 9.1. This build is pinned to AGP 9.0 by the
                // KSP problem in gradle.properties, so the ceiling on the
                // lifecycle version is really that pin showing up somewhere
                // new. It lifts when AGP does.
                api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.6")
            }
        }

        // wasmJsTest rather than commonTest: runComposeUiTest needs a real UI
        // environment, and Android's local unit-test variant -- which is what
        // :ui:allTests runs -- is not one. Covering the Android theme this way
        // needs an instrumented test on an emulator, which this project's CI
        // does not have.
        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test"))

                // Compose's own test harness. The point of using it rather
                // than asserting on plain values is that it composes for
                // real: a theme that compiles but throws on first composition
                // would still pass a value-level test.
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
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
