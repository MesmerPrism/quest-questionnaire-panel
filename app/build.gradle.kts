plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

fun String.toBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val updateManifestUrl = providers.gradleProperty("questQuestionnaireUpdateManifestUrl")
    .orElse("")
    .get()
val appVersionCode = providers.gradleProperty("questQuestionnaireVersionCode")
    .map(String::toInt)
    .orElse(1)
    .get()
val appVersionName = providers.gradleProperty("questQuestionnaireVersionName")
    .orElse("0.1.0")
    .get()

android {
    namespace = "io.github.mesmerprism.questquestionnaire.panel"
    compileSdk = 34
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.mesmerprism.questquestionnaire.panel"
        minSdk = 29
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            updateManifestUrl.toBuildConfigString()
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(project(":questionnaire-contract-core"))
    implementation(project(":brb-questionnaire-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
