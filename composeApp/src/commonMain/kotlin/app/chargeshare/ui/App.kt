package app.chargeshare.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/** Hauptnavigation (Tabs) – entspricht den Bereichen der bisherigen SwiftUI-App. */
enum class MainTab(val title: String) {
    Map("Karte"),
    Bookings("Buchungen"),
    Host("Hosten"),
    Profile("Profil"),
}

/**
 * Gemeinsamer UI-Einstieg fuer Android, iOS, Desktop und Web.
 * @param apiBaseUrl Server-Wurzel, z. B. `http://10.0.2.2:8080` (Android-Emulator)
 */
@Composable
fun App(apiBaseUrl: String = "http://localhost:8080") {
    ChargeShareTheme {
        var tab by remember { mutableStateOf(MainTab.Map) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = t == tab,
                            onClick = { tab = t },
                            icon = { Text(t.title.take(1)) },
                            label = { Text(t.title) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ChargeShare", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text(tab.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Text("API: $apiBaseUrl", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
