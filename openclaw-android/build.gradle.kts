// OpenClaw Android - Gradle library module
// Source: OpenClaw/apps/android/app (modified: application → library)
// SYNC: To update, run: rsync -a --exclude='*.iml' --exclude='.gradle' --exclude='build/'
//       /path/to/OpenClaw/apps/android/app/ openclaw-android/
//       Then re-apply this build.gradle.kts and the AndroidManifest.xml changes.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ai.openclaw.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        // SMS and call log enabled by default (sideloaded, not Play Store)
        buildConfigField("boolean", "OPENCLAW_ENABLE_SMS", "true")
        buildConfigField("boolean", "OPENCLAW_ENABLE_CALL_LOG", "true")
        // Version info (from OpenClaw 2026.3.20)
        buildConfigField("String", "VERSION_NAME", "\"2026.3.20\"")
        buildConfigField("int", "VERSION_CODE", "2026032000")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/*.version",
                    "/META-INF/LICENSE*.txt",
                    "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                    "DebugProbesKt.bin",
                    "kotlin-tooling-metadata.json",
                    "org/bouncycastle/pqc/crypto/picnic/lowmcL1.bin.properties",
                    "org/bouncycastle/pqc/crypto/picnic/lowmcL3.bin.properties",
                    "org/bouncycastle/pqc/crypto/picnic/lowmcL5.bin.properties",
                    "org/bouncycastle/x509/CertPathReviewerMessages*.properties",
                )
        }
    }

    lint {
        disable +=
            setOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "IconLauncherShape",
                "NewerVersionAvailable",
            )
        warningsAsErrors = false
    }
}


dependencies {
    implementation(project(":base"))

    val composeBom = platform("androidx.compose:compose-bom:2026.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.webkit:webkit:1.15.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material Components (XML theme + resources)
    implementation("com.google.android.material:material:1.13.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    // Coil for Compose image loading (URL images in chat)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Live2D Cubism SDK for avatar rendering
    implementation(project(":live2d-core"))
    implementation(project(":live2d-framework"))

    // Rive animation engine for AIBI-style character avatar
    implementation("app.rive:rive-android:11.3.1")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("org.commonmark:commonmark:0.27.1")
    implementation("org.commonmark:commonmark-ext-autolink:0.27.1")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.27.1")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.27.1")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.27.1")

    // CameraX (for node.invoke camera.* parity)
    implementation("androidx.camera:camera-core:1.5.2")
    implementation("androidx.camera:camera-camera2:1.5.2")
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-video:1.5.2")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Unicast DNS-SD (Wide-Area Bonjour) for tailnet discovery domains.
    implementation("dnsjava:dnsjava:3.6.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
}
