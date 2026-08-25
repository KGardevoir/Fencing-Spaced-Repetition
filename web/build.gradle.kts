@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// The browser build. Today it produces a smoke page rather than the app: it
// loads the real :shared scheduling core in a browser, shows what it computes,
// and draws the shared Compose theme on a canvas beneath it.
//
// The screens follow, from :ui. Keeping the entry point here rather than in
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
