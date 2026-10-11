plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.smileproc"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Placeholder until the human sets the real identifiers (plan Appendix 7).
        applicationId = "com.example.smileproc"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Claude's test app, "SmileProc (debug)": includes app/src/debug (probe and debug events).
        debug {
            applicationIdSuffix = ".debug"
        }
        // Release signing is set up with the human in M10.
        release {
            isMinifyEnabled = false
        }
        // The human's daily app from M5: release code and resources, signed with the debug key.
        create("dogfood") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // content/ JSON is loaded at runtime from assets.
        getByName("main") {
            assets.directories.add(rootProject.file("content").path)
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
