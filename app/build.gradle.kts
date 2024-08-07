import java.util.Properties

plugins {
    id("com.android.application")
}

val keystorePropsFile = File(project.rootDir, ".keystore.properties")
val keystoreProps = Properties()

if (keystorePropsFile.exists()) {
    keystoreProps.load(keystorePropsFile.inputStream())
}

android {
    compileSdk = 34

    namespace = "com.kvaster.mobell"

    defaultConfig {
        applicationId = "com.kvaster.mobell"
        minSdk = 23
        targetSdk = 34
        versionCode = 12
        versionName = "1.8.0"
        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                cppFlags.add("-std=c++17")
            }
        }
    }
    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                val keystoreFile = File(project.rootDir, keystoreProps.getProperty("store.file"))
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = keystoreProps.getProperty("store.password")
                    keyAlias = keystoreProps.getProperty("key.alias")
                    keyPassword = keystoreProps.getProperty("key.password")
                }
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "FULL"
            }

            if (signingConfigs.getByName("release").storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            isDebuggable = true
        }
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    productFlavors {
    }
    resourcePrefix("mobell")
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.preference)
    implementation(libs.kotlinStd)
}
