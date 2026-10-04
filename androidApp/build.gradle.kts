plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.chargeshare.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.chargeshare"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Server-Adresse: Standard = Host-Rechner aus dem Emulator. Ändern: -Pchargeshare.apiUrl=http://192.168.0.10:8080
        val apiUrl = providers.gradleProperty("chargeshare.apiUrl").orElse("http://10.0.2.2:8080").get()
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    // Zahlungsfenster mit Google Pay (PaymentSheet)
    implementation(libs.stripe.android)
}
