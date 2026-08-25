@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// The browser build: the scheduling core's own output, and beneath it the
// first real screen of the app -- :ui's OpponentsScreen over the browser
// database, both unmodified from what Android runs.
//
// The remaining screens follow. Keeping the entry point here rather than in
// :shared or :ui means both of those stay libraries, and the Android build
// never sees any of this.
kotlin {
    jvmToolchain(17)

    wasmJs {
        outputModuleName.set("fencing-web")

        // :shared addresses the SQLite worker relative to import.meta.url,
        // and this is the module that actually gets bundled, so the format
        // has to be settled here too. index.html already loads the bundle
        // with type="module".
        useEsModules()

        browser {
            commonWebpackConfig {
                outputFileName = "fencing-web.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":shared"))

                // The shared Compose user interface. Today the page uses it
                // only for the theme; the screens follow.
                implementation(project(":ui"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
    }
}
