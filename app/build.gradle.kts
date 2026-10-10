import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing comes from environment variables (CI) or a git-ignored keystore.properties
// file in the project root. Nothing secret is ever read from a tracked file.
val signingProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(name)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("FINANCES_KEYSTORE_PATH")
val releaseStorePassword = signingValue("FINANCES_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("FINANCES_KEY_ALIAS")
val releaseKeyPassword = signingValue("FINANCES_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists() &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.teppe21.finances"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.teppe21.finances"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // The UI is English-only; drop other locales contributed by libraries.
        resourceConfigurations += listOf("en")

        // Only set these after the policy is publicly reachable and the contact is real.
        buildConfigField("String", "PRIVACY_POLICY_URL", quoted(providers.gradleProperty("finances.privacyPolicyUrl").orNull ?: ""))
        buildConfigField("String", "PRIVACY_CONTACT", quoted(providers.gradleProperty("finances.privacyContact").orNull ?: ""))
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            // Never fall back to the debug key: without credentials the release tasks fail below.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Store native libraries uncompressed and page-aligned (required for 16 KB page sizes).
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }

    // Exported Room schemas are needed by the migration tests.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Reviewed baseline-free: new lint errors fail the build.
        warningsAsErrors = false
    }
}

// Fail release packaging early and clearly when no signing credentials are configured.
gradle.taskGraph.whenReady {
    val releasePackaging = allTasks.any { task ->
        val name = task.name.lowercase()
        (name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("package")) && name.contains("release")
    }
    if (releasePackaging && !hasReleaseSigning) {
        throw GradleException(
            "Release signing is not configured. Provide FINANCES_KEYSTORE_PATH, FINANCES_KEYSTORE_PASSWORD, " +
                "FINANCES_KEY_ALIAS and FINANCES_KEY_PASSWORD as environment variables or in keystore.properties."
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.02.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // 1.4.x ships 16 KB page-aligned native code (1.3.x did not).
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // On-device text recognition (Latin script model bundled in the APK).
    // 16.0.1 is the 16 KB page-size update of the bundled model (ML Kit release notes, Aug 2024).
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // android.jar's org.json is a stub in local unit tests; use the real implementation.
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
