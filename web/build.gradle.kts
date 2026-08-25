@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// The browser build. Today it produces a smoke page rather than the app: it
// loads the real :shared scheduling core in a browser and shows what it
// computes, which is the first artifact of this port anyone can actually open.
//
// The Compose UI will land here later. Keeping it out of :shared means that
// module stays a library with no entry point, and the Android build never sees
// any of this.
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
            }
        }
    }
}
