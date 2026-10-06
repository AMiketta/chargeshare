package app.chargeshare.ui.auth

/** Ein Konto aus dem Dev-Seed des Servers (`DevSeed`), nur für die Schnellanmeldung in Debug-Builds. */
data class DemoAccount(val label: String, val email: String, val password: String)

/** Demo-Konten des lokalen Backends; reine Testdaten, kein Geheimnis. Sichtbar nur bei `AppContainer.showDebugInfo`. */
object DemoAccounts {
    val Driver = DemoAccount(label = "Demo Fahrer", email = "demo-driver@chargeshare.local", password = "demo1234")
    val Host = DemoAccount(label = "Demo Host", email = "demo-host@chargeshare.local", password = "demo1234")
    val all = listOf(Driver, Host)
}
