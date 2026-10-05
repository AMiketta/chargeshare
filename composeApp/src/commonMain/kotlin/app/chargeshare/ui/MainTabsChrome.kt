package app.chargeshare.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plattform-Chrome für die vier Haupt-Tabs.
 *
 * - **iOS:** echte [platform.UIKit.UITabBarController]-Tabbar über cmp-ios-chrome (`NativeTabBar`,
 *   iOS 18+ `UITab` / Liquid Glass auf neueren Systemen).
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
