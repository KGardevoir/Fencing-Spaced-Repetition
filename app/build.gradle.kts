plugins {
    id("com.android.application")
    // Applied explicitly because gradle.properties sets android.builtInKotlin=false.
    // AGP 9 would otherwise supply the Kotlin plugin itself, and that is off for
    // the reason spelled out in gradle.properties.
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val gitCommit: String = try {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().readLine()?.trim() ?: "unknown"
} catch (e: Exception) {
    "unknown"
}

android {
    namespace = "com.fencing.spacedrepetition"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fencing.spacedrepetition"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }

    signingConfigs {
        create("release") {
            // For security, these should be set via environment variables or gradle.properties
            // Never commit actual keystore credentials to version control
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release-keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
        create("sharedDebug") {
            // Shared debug keystore supplied via GitHub Actions secrets (DEBUG_KEYSTORE_BASE64).
            // When DEBUG_KEYSTORE_FILE is not set (local dev) Android uses the default debug keystore.
            fun env(key: String, default: String) = System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() } ?: default
            storeFile = file(env("DEBUG_KEYSTORE_FILE", "debug-keystore.jks"))
            storePassword = env("DEBUG_KEYSTORE_PASSWORD", "android")
            keyAlias = env("DEBUG_KEY_ALIAS", "androiddebugkey")
            keyPassword = env("DEBUG_KEY_PASSWORD", "android")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            // Use shared signing when DEBUG_KEYSTORE_FILE is provided (e.g. CI via GitHub Secrets)
            // so that the APK can be installed over a previous CI build without uninstalling.
            if (System.getenv("DEBUG_KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("sharedDebug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only sign if keystore file exists
            val keystoreFile = file(System.getenv("KEYSTORE_FILE") ?: "release-keystore.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else if (System.getenv("DEBUG_KEYSTORE_FILE") != null) {
                // Nightly CI builds sign releases with the shared debug keystore
                // so they install over CI debug builds without a real release key.
                signingConfig = signingConfigs.getByName("sharedDebug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Platform-independent core: scheduling algorithms, settings constants,
    // clock, and the whole Room data layer -- entities, DAOs and the database
    // itself. :shared exposes Room and coroutines through api(), so the
    // coordinates that used to be listed here live there now.
    implementation(project(":shared"))

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager for scheduled background backups
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DocumentFile for writing backups to a user-selected SAF folder
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Coroutines. The version tracks :shared's coroutines-core: the -android
    // artifact is a companion to a specific core version, and letting Gradle
    // resolve a newer core underneath an older -android is how Dispatchers.Main
    // breaks at runtime rather than at build time.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.01.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
