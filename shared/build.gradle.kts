import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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
            api(libs.room.runtime)
        }
        iosMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
    }
}

// P20.2: the database lives here; schema JSONs (1…8) moved from app/schemas unchanged.
room {
    schemaDirectory("$projectDir/schemas")
}

// KSP 2.3 does not wire its Android output into the `com.android.kotlin.multiplatform.library`
// compilation (iOS targets are wired); register it by hand.
kotlin.sourceSets.named("androidMain") {
    kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/android/androidMain/kotlin"))
}
tasks.matching { it.name == "compileAndroidMain" }.configureEach { dependsOn("kspAndroidMain") }

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
