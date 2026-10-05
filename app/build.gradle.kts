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
        versionCode = 53
        versionName = "0.2.0-rc.44"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        create("release") {
            storeFile = releaseSigning.getProperty("storeFile")?.let { rootProject.file(it) }
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("com.google.android.material:material:1.13.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
