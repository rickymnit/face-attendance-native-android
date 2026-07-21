val configuredDebugApiBaseUrl = providers.gradleProperty("SCHOOLLOG_DEBUG_API_BASE_URL")
    .orElse(providers.environmentVariable("SCHOOLLOG_DEBUG_API_BASE_URL"))
    .orElse("http://10.0.2.2:3000/")
    .get()
val configuredStagingApiBaseUrl = providers.gradleProperty("SCHOOLLOG_STAGING_API_BASE_URL")
    .orElse(providers.environmentVariable("SCHOOLLOG_STAGING_API_BASE_URL"))
    .orElse("http://10.0.2.2:3000/")
    .get()
val configuredProductionApiBaseUrl = providers.gradleProperty("SCHOOLLOG_API_BASE_URL")
    .orElse(providers.environmentVariable("SCHOOLLOG_API_BASE_URL"))
    .orElse("https://api.schoollog.example.com/")
    .get()
val configuredDebugMockSyncEnabled = providers.gradleProperty("SCHOOLLOG_MOCK_SYNC_ENABLED")
    .orElse(providers.environmentVariable("SCHOOLLOG_MOCK_SYNC_ENABLED"))
    .orElse("false")
    .get()
    .toBooleanStrictOrNull() ?: false

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.schoollog.attendance"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.schoollog.attendance"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            buildConfigField("String", "API_BASE_URL", "\"$configuredDebugApiBaseUrl\"")
            buildConfigField("String", "ENVIRONMENT", "\"debug\"")
            buildConfigField("Boolean", "MOCK_SYNC_ENABLED", configuredDebugMockSyncEnabled.toString())
        }
        create("staging") {
            initWith(getByName("debug"))
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "API_BASE_URL", "\"$configuredStagingApiBaseUrl\"")
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
            buildConfigField("Boolean", "MOCK_SYNC_ENABLED", "false")
        }
        release {
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField("String", "API_BASE_URL", "\"$configuredProductionApiBaseUrl\"")
            buildConfigField("String", "ENVIRONMENT", "\"production\"")
            buildConfigField("Boolean", "MOCK_SYNC_ENABLED", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.mlkit.face.detection)
    implementation(libs.tensorflow.lite)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
