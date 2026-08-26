plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.localassistant.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.localassistant.app"
        minSdk = 26          // AccessibilityService + MediaPipe LLM требуют современный API
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        viewBinding = true
    }

    // Модель кладём в assets и упаковываем в APK, чтобы всё работало без сети
    androidResources {
        noCompress += listOf("task", "tflite", "bin")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // MediaPipe LLM Inference API — офлайн-инференс на устройстве
    implementation("com.google.mediapipe:tasks-genai:0.10.24")

    // Офлайн STT (Vosk, полностью локальный, без Google-сервисов)
    implementation("com.alphacephei:vosk-android:0.3.70")
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
