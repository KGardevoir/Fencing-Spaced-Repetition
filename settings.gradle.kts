pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // PREFER_SETTINGS rather than FAIL_ON_PROJECT_REPOS because the Kotlin
    // plugin registers its own project-level repository for the Node runtime
    // that the wasmJs browser tests need, and FAIL_ON_PROJECT_REPOS rejects
    // the registration itself -- it never reaches resolution, so no amount of
    // declaring the same repository here satisfies it.
    //
    // PREFER_SETTINGS ignores project-declared repositories instead of
    // failing, so the plugin's copy is discarded and the declarations below
    // are what actually serve the download. The property worth keeping is
    // preserved: every artifact still comes from a location named in this
    // file, and anything a plugin tries to add quietly is ignored.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // Node and yarn, for the karma runner behind the wasmJs browser tests.
        ivy("https://nodejs.org/dist") {
            name = "Node.js distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

rootProject.name = "FencingSpacedRepetition"
include(":shared")
include(":web")
include(":app")
