plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStorePath = providers.environmentVariable("QETARA_SIGNING_STORE").orNull
val signingStorePassword = providers.environmentVariable("QETARA_SIGNING_STORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("QETARA_SIGNING_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("QETARA_SIGNING_KEY_PASSWORD").orNull
val signingValues = listOf(signingStorePath, signingStorePassword, signingKeyAlias, signingKeyPassword)
val signingConfigured = signingValues.all { !it.isNullOrBlank() }
require(signingValues.all { it.isNullOrBlank() } || signingConfigured) {
    "Configure all four QETARA_SIGNING_* variables, or leave all unset for an unsigned release."
}

android {
    namespace = "com.example.wifidrop"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.wifidrop"
        minSdk = 24
        targetSdk = 36
        versionCode = providers.gradleProperty("qetaraVersionCode").getOrElse("5").toInt()
        versionName = project.version.toString()
    }

    signingConfigs {
        if (signingConfigured) {
            create("distribution") {
                storeFile = file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("distribution")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(project(":protocol"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("kr.jclab:noise-java:0.0.1")

    testImplementation("junit:junit:4.13.2")
}
