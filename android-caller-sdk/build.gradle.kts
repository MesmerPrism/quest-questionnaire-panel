plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "io.github.mesmerprism.questquestionnaire.sdk"
    compileSdk = 34
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 29
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
    api(project(":questionnaire-contract-core"))
    implementation(libs.androidx.core.ktx)
}

