plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "io.github.mesmerprism.questquestionnaire.unity"
    compileSdk = 34

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

val unityCallerRuntimeDeps by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = true
}

dependencies {
    implementation(project(":android-caller-sdk"))
    unityCallerRuntimeDeps("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    unityCallerRuntimeDeps(libs.androidx.core.unity)
}

tasks.register<Sync>("packageUnityCallerArtifacts") {
    group = "build"
    description = "Collects Unity caller Android artifacts and runtime dependencies for plain Unity projects."
    dependsOn(
        "assembleDebug",
        ":android-caller-sdk:assembleDebug",
        ":questionnaire-contract-core:jar"
    )
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    into(layout.buildDirectory.dir("unity-caller-artifacts"))

    from(layout.buildDirectory.file("outputs/aar/unity-caller-plugin-debug.aar")) {
        into("plugins")
        rename { "quest-questionnaire-unity-caller-debug.aar" }
    }
    from(project(":android-caller-sdk").layout.buildDirectory.file("outputs/aar/android-caller-sdk-debug.aar")) {
        into("plugins")
        rename { "quest-questionnaire-android-caller-sdk-debug.aar" }
    }
    from(project(":questionnaire-contract-core").layout.buildDirectory.file("libs/questionnaire-contract-core.jar")) {
        into("plugins")
        rename { "quest-questionnaire-contract-core.jar" }
    }
    from(unityCallerRuntimeDeps) {
        into("runtime-deps")
    }
    from(rootProject.file("examples/native-caller/src/main/res/xml/questionnaire_result_paths.xml")) {
        into("res/xml")
    }
}
