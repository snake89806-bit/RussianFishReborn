plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.reborn.fishing"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.reborn.fishing"
        minSdk = 23
        targetSdk = 35
        versionCode = 28
        versionName = "0.28.0"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
