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
        moduleName = "fencing-web"
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
