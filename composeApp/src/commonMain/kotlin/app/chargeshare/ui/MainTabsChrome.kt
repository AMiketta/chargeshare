package app.chargeshare.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plattform-Chrome für die vier Haupt-Tabs.
 *
 * - **iOS:** echte UIKit-`UITabBar` (SF Symbols, Systemoptik / Liquid Glass ab iOS 26), die als transluzentes Overlay
 *   **über** dem Tab-Inhalt liegt (edge-to-edge: Karte und Listen laufen unter der Leiste durch). Wie weit die Leiste
 *   den Inhalt überdeckt, steht in [LocalTabBarOverlap]. Der Tab-Inhalt bleibt in derselben Compose-Komposition.
 * - **Android / Desktop / Web:** bestehende Compose-[app.chargeshare.ui.components.AppNavigationBar] im `Scaffold`;
 *   der Inhalt endet über der Leiste, [LocalTabBarOverlap] ist dort `0.dp`.
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
