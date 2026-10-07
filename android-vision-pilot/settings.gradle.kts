val gwellNexusUser = System.getenv("GWELL_NEXUS_USER").orEmpty()
val gwellNexusPassword = System.getenv("GWELL_NEXUS_PASSWORD").orEmpty()

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://mvn.zztfly.com/android") }
        maven { url = uri("https://nexus-sg.gwell.cc/nexus/repository/maven-releases/") }
        if (gwellNexusUser.isNotBlank() && gwellNexusPassword.isNotBlank()) {
            maven {
                url = uri("https://nexus-sg.gwell.cc/nexus/repository/maven-gwiot/")
                credentials {
                    username = gwellNexusUser
                    password = gwellNexusPassword
                }
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "SMART24VisionPilot"
include(":app")
