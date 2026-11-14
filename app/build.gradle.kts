plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

// Monotonic versioning ensures Android treats every new APK as an in-place update
// rather than failing or prompting to replace data; never leave versionCode pinned at 1.
val versionPropsText = providers
    .fileContents(layout.projectDirectory.file("version.properties"))
    .asText
    .orNull
val versionPropsMap = versionPropsText
    ?.lineSequence()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
    ?.associate { line ->
        val idx = line.indexOf('=')
        line.substring(0, idx).trim() to line.substring(idx + 1).trim()
    }
    .orEmpty()
val propsVersionCode = versionPropsMap["versionCode"]?.toIntOrNull() ?: 1
val propsVersionName = versionPropsMap["versionName"]
    ?.takeIf { it.isNotEmpty() }
    ?: "0.0.1"
val gitCommitCount = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().toIntOrNull() ?: 0 }.getOrElse(0)
}.getOrDefault(0)
val overrideVersionCode = providers.gradleProperty("smileproc.versionCode")
    .orNull
    ?.trim()
    ?.toIntOrNull()
val overrideVersionName = providers.gradleProperty("smileproc.versionName")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
val resolvedVersionCode = (overrideVersionCode ?: maxOf(propsVersionCode, gitCommitCount))
    .coerceAtLeast(1)
val resolvedVersionName = overrideVersionName ?: if (
    resolvedVersionCode > propsVersionCode && propsVersionName == "0.0.$propsVersionCode"
) {
    "0.0.$resolvedVersionCode"
} else {
    propsVersionName
}

android {
    namespace = "com.example.smileproc"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Placeholder until the human sets the real identifiers (plan Appendix 7).
        applicationId = "com.example.smileproc"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
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
