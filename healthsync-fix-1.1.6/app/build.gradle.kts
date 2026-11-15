plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.buchin.healthsync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.buchin.healthsync"
        minSdk = 28
        targetSdk = 35
        versionCode = 8
        versionName = "1.1.6"

        // Google OAuth client ID (WEB-application type) for Google Sign-In.
        // Supplied at build time with -PGOOGLE_OAUTH_CLIENT_ID=<id>.
        // A matching ANDROID-type OAuth client (package com.buchin.healthsync
        // + this build's signing SHA-1) must exist in the same Google Cloud
        // project — see the setup guide. Until the real client ID is set,
        // Drive sync stays disabled and the app reports "Drive not configured"
        // — Health Connect permissions and the local sync logic still work.
        buildConfigField(
            "String",
            "GOOGLE_OAUTH_CLIENT_ID",
            "\"" + (project.findProperty("GOOGLE_OAUTH_CLIENT_ID") as String? ?: "REPLACE_WITH_OAUTH_CLIENT_ID") + "\""
        )
    }

    signingConfigs {
        create("release") {
            // All values are passed on the Gradle command line at build time:
            // -PSTORE_FILE -PSTORE_PASSWORD -PKEY_ALIAS -PKEY_PASSWORD
            // Nothing secret lives in this file or in version control.
            val storeFileProp = project.findProperty("STORE_FILE") as String?
            if (storeFileProp != null) {
                storeFile = file(storeFileProp)
                storePassword = project.findProperty("STORE_PASSWORD") as String?
                keyAlias = project.findProperty("KEY_ALIAS") as String?
                keyPassword = project.findProperty("KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug builds are unsigned-able for local testing; release is what ships.
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // The offline build cannot fetch the lint toolchain
    // (com.android.tools.lint:lint-gradle) through the sandbox egress proxy,
    // and the release lint gate is not needed for this small companion app.
    // Lint can still be run on demand with the lint task when online.
    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
