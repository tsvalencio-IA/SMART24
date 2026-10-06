pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://mvn.zztfly.com/android") }
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "SMART24VisionPilot"
include(":app")
