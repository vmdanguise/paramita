// AGP 9 trae Kotlin integrado: no hace falta el plugin org.jetbrains.kotlin.android (ver ADR-0007).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
