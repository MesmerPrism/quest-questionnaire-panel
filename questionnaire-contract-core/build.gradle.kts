plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("contract/examples"))
    }
}

dependencies {
    implementation(libs.json)

    testImplementation(libs.junit)
}

