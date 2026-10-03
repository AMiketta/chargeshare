import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val androidEnabled = gradle.extra["chargeshare.androidEnabled"] as Boolean
val iosEnabled = gradle.extra["chargeshare.iosEnabled"] as Boolean

if (androidEnabled) apply(plugin = libs.plugins.android.kmp.library.get().pluginId)

// AGP 9: Android-Konfiguration einer KMP-Library (Plugin wird nur bei vorhandenem SDK angewendet)
if (androidEnabled) {
    (kotlin as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
        namespace = "app.chargeshare.shared"
        compileSdk = 37
        minSdk = 24
        withHostTest { }
    }
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    if (iosEnabled) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach {
            it.binaries.framework {
                baseName = "Shared"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        if (androidEnabled) {
            androidMain.dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        if (iosEnabled) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}
