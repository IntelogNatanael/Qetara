import java.io.File
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":protocol"))
    implementation("kr.jclab:noise-java:0.0.1")
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

compose.desktop {
    application {
        mainClass = "com.example.wifidrop.pc.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Qetara"
            packageVersion = project.version.toString()
            description = "Qetara"
            vendor = "Qetara contributors"
            copyright = "Copyright 2026 Qetara contributors"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.desktop", "java.logging", "java.prefs", "jdk.crypto.ec", "jdk.unsupported", "jdk.accessibility")
            macOS {
                iconFile.set(project.file("src/main/resources/qetara.icns"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/qetara.png"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/qetara.ico"))
                menu = true
                menuGroup = "Qetara"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "042ce66b-d923-4b77-a354-f4dcc6ea5244"
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}


// Compose 1.8.2 treats javaHome as internal. Track the actual packaging JDK
// and its release metadata so switching/updating Java regenerates native images.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJvmToolOperationTask>().configureEach {
    inputs.property("qetaraPackagingJavaHome", javaHome)
    inputs.file(javaHome.map { selectedHome -> File(selectedHome, "release") })
        .withPropertyName("qetaraPackagingJavaRelease")
        .withPathSensitivity(PathSensitivity.NONE)
}

// Manual, offline rendering of production composables with synthetic state. No JUnit/network run.
tasks.register<JavaExec>("designPreview") {
    group = "verification"
    description = "Render desktop design previews with ImageComposeScene, without windows or network."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.example.wifidrop.pc.DesktopDesignPreview")
    workingDir(rootProject.projectDir)
    args(
        rootProject.layout.projectDirectory.dir(".local/design-review").asFile.absolutePath,
        providers.gradleProperty("previewScenario").getOrElse("all")
    )
    systemProperty("java.awt.headless", "true")
}
