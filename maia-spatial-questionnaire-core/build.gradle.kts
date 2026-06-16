plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":questionnaire-contract-core"))
    implementation(libs.json)
    testImplementation(libs.junit)
}
