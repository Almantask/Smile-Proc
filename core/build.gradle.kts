import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

val fixturesDir = rootProject.layout.projectDirectory.dir("fixtures")
val contentDir = rootProject.layout.projectDirectory.dir("content")
val stringsFile = rootProject.layout.projectDirectory.file("app/src/main/res/values/strings.xml")

tasks.test {
    // Shared test data (docs/spec/data.md › Fixtures), the runtime content JSON, and the app's
    // strings (checked against the ids and copy the spec names).
    inputs.dir(fixturesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(contentDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(stringsFile).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("smileproc.fixtures", fixturesDir.asFile.absolutePath)
    systemProperty("smileproc.content", contentDir.asFile.absolutePath)
    systemProperty("smileproc.strings", stringsFile.asFile.absolutePath)
}
