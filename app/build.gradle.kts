plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.reborn.fishing"
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    defaultConfig {
        applicationId = "ru.reborn.fishing"
        minSdk = 23
        targetSdk = 35
        versionCode = 29
        versionName = "0.29.0"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
