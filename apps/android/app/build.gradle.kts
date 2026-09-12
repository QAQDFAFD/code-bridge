import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.codebridge.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.codebridge.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    // QR pairing: camera preview + barcode decoding (no Play Services needed)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    // Background auto-connect: probes paired Macs whenever the network changes
    implementation(libs.androidx.work.runtime)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // MockWebServer drives RelayClient against a real local HTTP server;
    // the real org.json artifact replaces Android's "not mocked" stubs in JVM tests.
    testImplementation(libs.mockwebserver)
    testImplementation(libs.org.json)
}
