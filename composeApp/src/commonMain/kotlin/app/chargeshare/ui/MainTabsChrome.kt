package app.chargeshare.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plattform-Chrome für die vier Haupt-Tabs.
 *
 * - **iOS:** echte UIKit-`UITabBar` (SF Symbols, Systemoptik / Liquid Glass ab iOS 26); der Tab-Inhalt
 *   bleibt in derselben Compose-Komposition (kein verschachtelter `ComposeUIViewController` pro Tab).
 * - **Android / Desktop / Web:** bestehende Compose-[app.chargeshare.ui.components.AppNavigationBar].
 *
 * Vollbild-Screens (Detail, Buchung, …) liegen weiterhin oberhalb und ersetzen diese Chrome.
 */
@Composable
expect fun MainTabsChrome(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    searchContent: @Composable () -> Unit,
    bookingsContent: @Composable () -> Unit,
    hostContent: @Composable () -> Unit,
    profileContent: @Composable () -> Unit,
)
