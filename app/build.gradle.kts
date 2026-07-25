plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

android {
    namespace   = "com.thejaustin.pearity"
    compileSdk  = 35

    defaultConfig {
        applicationId   = "com.thejaustin.pearity"
        minSdk          = 31          // S22 Ultra launched on Android 12
        targetSdk       = 35
        // CI injects versionCode/versionName via -PversionCode= -PversionName=
        versionCode     = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName     = (findProperty("versionName") as String?) ?: "1.0.0-alpha.dev"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    }

    lint {
        // AGP 8.7.3's bundled Lint crashes analyzing Kotlin test sources against the newer
        // compose-runtime lint checks pulled in by Material3 1.4.0 stable (Kotlin Analysis API
        // version skew: "Found class KaSimpleVariableAccessCall, but interface was expected").
        // This is a lint-tooling bug (confirmed by lint's own crash message), not an app issue.
        disable += "FrequentlyChangingValue"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.prev)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.activity)
    implementation(libs.compose.animation)

    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.navigation.compose)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.datastore)
    implementation(libs.coroutines)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
