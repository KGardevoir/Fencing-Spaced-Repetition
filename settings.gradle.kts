pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // The Kotlin/Wasm browser tests run under karma, which needs a Node
        // runtime and yarn. The Kotlin plugin would normally register these
        // download locations itself, but it does so as project repositories,
        // which FAIL_ON_PROJECT_REPOS rejects. Declaring them here keeps that
        // guard in place -- every artifact still comes from a location named
        // in this file -- rather than relaxing it to PREFER_SETTINGS.
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
include(":app")
