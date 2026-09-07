plugins {
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

allprojects {
    group = "org.qetara"
    version = providers.gradleProperty("qetaraVersion").getOrElse("1.3.1")
}
