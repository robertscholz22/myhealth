import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.myhealth.shared"
        compileSdk = 36
        minSdk = 34
        compilations.configureEach {
            compileTaskProvider.configure { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
        }
    }
    // Built on macOS CI only (P21); on Linux these targets are skipped, but commonMain is still
    // compiled as metadata, which rejects any JVM-only API.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.okio)
        }
    }
}
