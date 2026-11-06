import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing comes from key.properties at the repository root, which is
// git-ignored and only written by CI. Without it the release build stays
// debug-signed, so `assembleRelease` still works but the APK cannot be uploaded.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}

android {
    namespace = "com.shieldra.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shieldra.app"
        minSdk = 26
        targetSdk = 36
        // Google Play rejects a repeated versionCode, so CI builds take the workflow run number (always
        // rising); a local build falls back to 2.
        versionCode = maxOf(2, System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2)
        versionName = "0.2.0"
        // Without this the androidTest sources compile into an APK that no instrumentation can run,
        // so `connectedDebugAndroidTest` fails before the first test class loads. The 17 security
        // classes (Keystore evidence, Room migrations, factory-reset key destruction) were being
        // built and never executed.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // A committed debug key (the standard Android debug credentials, not a secret) so every CI
        // build and local build share one signature. Without it each CI run signs with an ephemeral
        // key and `adb install -r` fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE on the test phone.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // Explicitly bind the committed debug key so CI never falls back to its ephemeral
        // ~/.android/debug.keystore (which changes per run and breaks `adb install -r`).
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        named("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.getProperty("storeFile") != null) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 + resource shrinking: a Compose app runs and starts noticeably faster once optimised.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        // The release code path signed with the committed debug key, so `adb install -r` upgrades the
        // owner's phone in place (data kept) and what they feel is the real, optimised app.
        create("phone") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    // Owner check before monitoring can be stopped (device biometric or screen-lock credential).
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // A-34: fused location for the protection guards' evidence (fine location, with owner consent).
    implementation(libs.play.services.location)
    // A-40: Google Play subscription for the Pro plan; confined to com.shieldra.billing by the group-c gate.
    implementation(libs.android.billing.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(kotlin("test-junit5"))
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
