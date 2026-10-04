import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val releaseSigning = Properties().apply {
    providers.environmentVariable("BETTER_LAUNCHER_SIGNING_PROPERTIES").orNull?.let { path ->
        file(path).inputStream().use { load(it) }
    }
}

android {
    namespace = "app.better.launcher"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.better.launcher"
        minSdk = 31
        targetSdk = 36
