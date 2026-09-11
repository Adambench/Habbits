import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing details, kept out of the repository.
 *
 * Locally this is `keystore.properties`; in CI the workflow writes the same
 * file from repository secrets. When it is absent the release build is simply
 * unsigned, so cloning and building the project never requires the key.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasSigningKey = keystoreProperties.getProperty("storeFile")
    ?.let { rootProject.file(it).exists() } == true

android {
    namespace = "io.github.adambench.habbits"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        // Placeholder until M7 — see PLAN.md §10.
        applicationId = "io.github.adambench.habbits"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("storeAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v1 is irrelevant at minSdk 26; v2/v3 are what get verified.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // A debug build installs alongside the release one instead of replacing it.
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}
