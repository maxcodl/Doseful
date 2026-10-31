import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.healthre"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "dev.maxpayne.doseful"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0"
    }

    // Releases use ./key.properties (storeFile, storePassword, keyAlias, keyPassword) if present,
    // otherwise fall back to the shared debug key. The debug key is public: fine for sideloading, not for a store.
    val keyProps = rootProject.file("key.properties")
    signingConfigs {
        if (keyProps.exists()) create("release") {
            val p = Properties().apply { keyProps.inputStream().use { load(it) } }
            storeFile = rootProject.file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }

    buildTypes {
        // Same key as release (when key.properties exists) so a debug build installs over a release one and keeps its data.
        debug {
            if (keyProps.exists()) signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = if (keyProps.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.kyant.backdrop)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.tooling)
}
