plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":protocol"))
    implementation("kr.jclab:noise-java:0.0.1")
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.example.wifidrop.pc.MainKt"
    }
}
