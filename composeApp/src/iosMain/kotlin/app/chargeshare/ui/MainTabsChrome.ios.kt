package app.chargeshare.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.amiketta.cmpioschrome.NativeTabBar

/**
 * Native UIKit-Tabbar (cmp-ios-chrome). Jeder Tab hostet eigenen Compose-Content;
 * Session-/Repo-State bitte über Flows beobachten (Lambdas werden in der UIKit-Factory einmal erfasst).
 */
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
    NativeTabBar(
        modifier = modifier,
        selectedIndex = selected.ordinal,
        onSelectedIndexChange = { index -> onSelect(MainTab.entries[index]) },
    ) {
        tab(title = MainTab.Search.title, systemImage = MainTab.Search.systemImage, content = searchContent)
        tab(title = MainTab.Bookings.title, systemImage = MainTab.Bookings.systemImage, content = bookingsContent)
        tab(title = MainTab.Host.title, systemImage = MainTab.Host.systemImage, content = hostContent)
        tab(title = MainTab.Profile.title, systemImage = MainTab.Profile.systemImage, content = profileContent)
    }
}
