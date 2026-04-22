// Hermes Agent Android - 1:1 复刻 hermes-agent (Python) 到 Kotlin
// 独立于 openclaw-android 模块，完全对齐 Hermes 架构

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.xiaomo.hermes.hermes"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Kotlin coroutines (替代 Python asyncio/threading)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // HTTP client (替代 Python requests/httpx) — 对齐 Operit 4.12.0
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON (替代 Python json)
    implementation("org.json:json:20250107")

    // YAML (替代 Python pyyaml)
    implementation("org.yaml:snakeyaml:2.4")

    // SQLite (替代 Python sqlite3, hermes_state.py)
    implementation("androidx.sqlite:sqlite-ktx:2.5.0")

    // OpenAI SDK compatible (替代 Python openai SDK) — 对齐 Operit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}
