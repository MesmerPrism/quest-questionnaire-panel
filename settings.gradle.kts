pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "quest-questionnaire-panel"
include(":questionnaire-contract-core")
include(":brb-questionnaire-core")
include(":android-caller-sdk")
include(":unity-caller-plugin")
include(":app")
include(":examples:native-caller")
