// Top-level build file: declares plugins once for the whole project.
// "apply false" = make the plugin available, but do not apply it here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}