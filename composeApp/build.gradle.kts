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
        // Compose-Multiplatform-Ressourcen (Schriften) landen als Android-Assets
        androidResources { enable = true }
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
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.kotlinx.datetime)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        // Custom dependsOn schaltet das Default Hierarchy Template aus (wie cmp-ios-chrome) –
        // deshalb iosMain / mapcomposeMain hier explizit verdrahten.
        val common = getByName("commonMain")

        // MapCompose + OSM nur für Android und Desktop; iOS nutzt MapKit, Web einen Stub.
        val mapcomposeMain = create("mapcomposeMain") {
            dependsOn(common)
            dependencies {
                implementation(libs.mapcompose)
            }
        }
        getByName("desktopMain").dependsOn(mapcomposeMain)
        if (androidEnabled) {
            getByName("androidMain").dependsOn(mapcomposeMain)
        }

        if (iosEnabled) {
            val iosMain = create("iosMain") {
                dependsOn(common)
            }
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        }

        // MainTabsChrome actuals: desktopMain / wasmJsMain / androidMain (Compose AppNavigationBar);
        // iosMain: Compose-Content + native UIKit-UITabBar + MapKit-Karte.

        sourceSets.getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            // Dispatchers.Main für viewModelScope auf dem Desktop (Swing-Event-Thread)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.resources {
    packageOfResClass = "app.chargeshare.resources"
}

compose.desktop {
    application {
        mainClass = "app.chargeshare.ui.DesktopMainKt"
    }
}
