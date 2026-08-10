plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.ciphrchat.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.ciphrchat.app"
        minSdk = 26
        targetSdk = 34
        // Bump the install identity so Android refreshes the launcher icon on upgrade.
        versionCode = 12
        versionName = "0.1.0-dev.12"
        val relayAddress = project.providers.gradleProperty("ciphrchatRelayAddress")
            .orElse(project.providers.environmentVariable("CIPHRCHAT_RELAY_ADDRESS"))
            .orElse("")
            .get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "CIPHRCHAT_RELAY_ADDRESS", "\"$relayAddress\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val releaseKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("KEY_ALIAS")
    val releaseKeyPassword = System.getenv("KEY_PASSWORD")
    val hasReleaseSigning = listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() } && file(releaseKeystorePath ?: "").isFile

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.register<Exec>("buildRust") {
    // Only run if specifically requested to avoid breaking standard Gradle syncs without NDK
    onlyIf { project.hasProperty("buildRust") }
    workingDir = file("../../crates/ciphrchat-ffi")
    commandLine = listOf("cargo", "ndk", "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86_64", "-o", "../../apps/android/src/main/jniLibs", "build", "--release")
}

tasks.whenTaskAdded {
    if (name == "mergeDebugJniLibFolders" || name == "mergeReleaseJniLibFolders") {
        dependsOn("buildRust")
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.zxing.core)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.androidx.core.uwb)
    implementation(libs.guava)
    implementation(libs.signal.protocol.java)

    testImplementation(libs.junit)

    ksp(libs.hilt.compiler)
    ksp(libs.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
