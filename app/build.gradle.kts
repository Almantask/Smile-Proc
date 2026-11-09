plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs(
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
            }
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    constraints {
        // biometric 1.1.0 pulls in fragment 1.2.5, whose FragmentActivity rejects the >16-bit
        // request codes activity 1.13 generates: every permission request and SAF launcher
        // crashed MainActivity. Not a new library, only a version floor (ActivityResultLaunchTest).
        implementation(libs.androidx.fragment) {
            because("fragment < 1.3 crashes ActivityResultRegistry launches in a FragmentActivity")
        }
    }
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room3.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room3.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
