plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Generates JSON parsing code for @Serializable classes (data layer DTOs)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wesley.seldonfleet"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.wesley.seldonfleet"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ViewModel inside Compose, created through a factory (UI layer only)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Lets the screen observe the ViewModel state safely
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Navigation between list and detail; keeps the list alive on the back stack (REQ-06)
    implementation(libs.androidx.navigation.compose)
    // JSON parsing for frota.json (data layer only; domain never imports it)
    implementation(libs.kotlinx.serialization.json)
    // Saves the set of favorite device IDs on disk (data layer only; REQ-07)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    // Runs the ViewModel test on the JVM, no emulator
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}