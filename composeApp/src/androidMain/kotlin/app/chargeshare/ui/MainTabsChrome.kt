package app.chargeshare.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.chargeshare.ui.components.AppNavigationBar

/** Compose-Tabbar (Design-System) für Android, Desktop und Web. */
@Composable
actual fun MainTabsChrome(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier,
    searchContent: @Composable () -> Unit,
    bookingsContent: @Composable () -> Unit,
    hostContent: @Composable () -> Unit,
    profileContent: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AppNavigationBar(
                items = MainTab.entries,
                selected = selected,
                title = MainTab::title,
                icon = MainTab::icon,
                onSelect = onSelect,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                MainTab.Search -> searchContent()
                MainTab.Bookings -> bookingsContent()
                MainTab.Host -> hostContent()
                MainTab.Profile -> profileContent()
            }
        }
    }
}
