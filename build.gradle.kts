// Declaring the Kotlin plugins here puts the catalog's Kotlin version on the build classpath;
// AGP's built-in Kotlin support then compiles :app with it.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
