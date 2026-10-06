pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("http://maven.bughd.com/public")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://maven.aliyun.com/nexus/content/repositories/releases/")
            isAllowInsecureProtocol = true
        }
    }
}
rootProject.name = "SMART24VisionPilot"
include(":app")
