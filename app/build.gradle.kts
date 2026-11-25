import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release credentials stay on the developer's machine and are ignored by Git.
val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}

val localRuntimeDir = rootProject.file("build/qwen-runtime")
val qwenRuntimeAvailable = localRuntimeDir.resolve("arm64-v8a/libqwen3_tts_jni.so").isFile
val qwenPrototype = providers.gradleProperty("flQwenPrototype").orNull?.toBoolean() ?: qwenRuntimeAvailable
val omniRuntimeDir = rootProject.file("build/omnivoice-runtime")
val omniRuntimeAvailable = listOf("libomnivoice.so", "libfl_omnivoice_jni.so").all {
    omniRuntimeDir.resolve("arm64-v8a/$it").isFile
}
val omniVoiceEnabled = providers.gradleProperty("flOmniVoice").orNull?.toBoolean() ?: omniRuntimeAvailable
val audioCppDir = rootProject.file("build/audiocpp-runtime")
val audioCppAvailable = audioCppDir.resolve("arm64-v8a/libfl_audiocpp_jni.so").isFile
val audioCppEnabled = providers.gradleProperty("flAudioCpp").orNull?.toBoolean() ?: audioCppAvailable
val offlineRelease = providers.gradleProperty("flOfflineRelease").orNull == "true"

android {
    if (releaseSigningFile.isFile) {
        signingConfigs {
            create("release") {
                fun requiredValue(name: String): String =
                    requireNotNull(releaseSigningProperties.getProperty(name)?.takeIf { it.isNotBlank() }) {
                        "Missing release signing property: $name"
                    }
                storeFile = rootProject.file(requiredValue("storeFile"))
                storePassword = requiredValue("storePassword")
                keyAlias = requiredValue("keyAlias")
                keyPassword = requiredValue("keyPassword")
            }
        }
    }
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Optional native libraries for the on-device Qwen debug prototype.
    if (qwenPrototype) {
        require(qwenRuntimeAvailable) {
            "Run scripts/prepare-qwen-prototype.ps1 before building the Qwen prototype."
        }
        sourceSets.getByName("debug").jniLibs.directories.add(localRuntimeDir.absolutePath)
    }
    if (omniVoiceEnabled) {
        require(omniRuntimeAvailable) { "Run scripts/prepare-omnivoice.ps1 first." }
        sourceSets.getByName("debug").apply {
            jniLibs.directories.add(omniRuntimeDir.absolutePath)
            assets.directories.add(rootProject.file("build/omnivoice-notices").absolutePath)
        }
    }
    if (audioCppEnabled) {
        require(audioCppAvailable) { "Run scripts/prepare-audiocpp.ps1 first." }
        sourceSets.getByName("debug").apply {
            jniLibs.directories.add(audioCppDir.absolutePath)
            assets.directories.add(rootProject.file("build/audiocpp-assets").absolutePath)
        }
    }
    if (offlineRelease) {
        require(qwenRuntimeAvailable && omniRuntimeAvailable && audioCppAvailable) {
            "Prepare all three offline runtimes before building the offline release."
        }
        sourceSets.getByName("release").apply {
            jniLibs.directories.addAll(listOf(localRuntimeDir, omniRuntimeDir, audioCppDir).map { it.absolutePath })
            assets.directories.addAll(listOf(
                rootProject.file("build/omnivoice-notices").absolutePath,
                rootProject.file("build/audiocpp-assets").absolutePath
            ))
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "QWEN_PROTOTYPE", qwenPrototype.toString())
            buildConfigField("boolean", "OMNIVOICE_ENABLED", omniVoiceEnabled.toString())
            buildConfigField("boolean", "AUDIOCPP_ENABLED", audioCppEnabled.toString())
            if (qwenPrototype || omniVoiceEnabled || audioCppEnabled) ndk.abiFilters.add("arm64-v8a")
        }
        release {
            buildConfigField("boolean", "QWEN_PROTOTYPE", offlineRelease.toString())
            buildConfigField("boolean", "OMNIVOICE_ENABLED", offlineRelease.toString())
            buildConfigField("boolean", "AUDIOCPP_ENABLED", offlineRelease.toString())
            if (offlineRelease) ndk.abiFilters.add("arm64-v8a")
            if (releaseSigningFile.isFile) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
