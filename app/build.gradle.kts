plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.anmol.focuuus"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.anmol.focuuus"
        minSdk = 31
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug key, so release builds install over debug ones and keep their data.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.13.0-alpha01")
    implementation("androidx.compose.foundation:foundation:1.13.0-alpha01")
    implementation("androidx.compose.material3:material3:1.5.0-alpha26")
}
