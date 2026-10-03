import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val androidEnabled = gradle.extra["chargeshare.androidEnabled"] as Boolean
val iosEnabled = gradle.extra["chargeshare.iosEnabled"] as Boolean

if (androidEnabled) apply(plugin = libs.plugins.android.kmp.library.get().pluginId)

// AGP 9: Android-Konfiguration einer KMP-Library (Plugin wird nur bei vorhandenem SDK angewendet)
if (androidEnabled) {
    (kotlin as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
        namespace = "app.chargeshare.composeapp"
        compileSdk = 37
        minSdk = 24
    }
}

kotlin {
    // Desktop-JVM: Vorschau/Entwicklung und Kompilierungs-Check auf jedem Host (auch Linux)
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    if (iosEnabled) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach {
            it.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        sourceSets.getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.chargeshare.ui.DesktopMainKt"
    }
}
