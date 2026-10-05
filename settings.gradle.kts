rootProject.name = "chargeshare"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// Android-Target ist optional: wird nur gebaut, wenn ein Android SDK gefunden wird
// (ANDROID_HOME / ANDROID_SDK_ROOT / local.properties sdk.dir) und nicht per
// `-Pchargeshare.android=false` abgeschaltet wurde. Explizit erzwingen: `-Pchargeshare.android=true`.
val androidFlag: String? = providers.gradleProperty("chargeshare.android").orNull
val sdkFromLocalProps: String? = file("local.properties").takeIf { it.exists() }
    ?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")
val sdkDir: String? = sdkFromLocalProps
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
val androidEnabled = when (androidFlag) {
    "true" -> true
    "false" -> false
    else -> sdkDir != null && file(sdkDir).isDirectory
}
gradle.extra["chargeshare.androidEnabled"] = androidEnabled

// iOS-Targets (Kotlin/Native) bauen nur auf macOS. Auf anderen Hosts werden sie nicht deklariert,
// ausser mit `-Pchargeshare.ios=true` erzwungen (dann nur Metadaten-/Klib-Kompilierung moeglich).
val iosFlag: String? = providers.gradleProperty("chargeshare.ios").orNull
val isMac = System.getProperty("os.name").lowercase().contains("mac")
gradle.extra["chargeshare.iosEnabled"] = when (iosFlag) {
    "true" -> true
    "false" -> false
    else -> isMac
}

include(":shared")
include(":composeApp")
include(":server")
include(":web")
if (androidEnabled) include(":androidApp")

// cmp-ios-chrome: öffentliche KMP-Lib (https://github.com/AMiketta/cmp-ios-chrome), Git-Submodule.
// Als Unterprojekt eingebunden (noch nicht auf Maven); iOS-/Android-Flags spiegeln ChargeShare.
val cmpIosChromeDir = file("third_party/cmp-ios-chrome/library")
if (cmpIosChromeDir.resolve("build.gradle.kts").isFile) {
    gradle.extra["cmpioschrome.androidEnabled"] = androidEnabled
    gradle.extra["cmpioschrome.iosEnabled"] = gradle.extra["chargeshare.iosEnabled"]
    include(":cmpIosChrome")
    project(":cmpIosChrome").projectDir = cmpIosChromeDir
}
