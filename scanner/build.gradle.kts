import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.maxpayne.doseful.scanner"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "dev.maxpayne.doseful.scanner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" } // keeps the add-on ~13 MB instead of ~41 MB
    }

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
}

dependencies {
    implementation(libs.activity.compose) // only for ComponentActivity + result contracts
    implementation(libs.core.ktx)
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
