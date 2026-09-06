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

rootProject.name = "Qetara"
include(":protocol")
if (!providers.gradleProperty("qetaraDesktopOnly").map(String::toBoolean).getOrElse(false)) {
    include(":app")
}
include(":pc")
