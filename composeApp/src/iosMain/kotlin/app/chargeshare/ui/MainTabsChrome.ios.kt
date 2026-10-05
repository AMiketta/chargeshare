package app.chargeshare.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UITabBar
import platform.UIKit.UITabBarDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObject

/**
 * Haupt-Tabs auf iOS: Tab-Inhalt bleibt in **derselben** Compose-Komposition (Theme, ImageLoader,
 * nutzerbezogener ViewModel-Speicher, Navigation), nur die Leiste selbst ist eine echte UIKit-[UITabBar]
 * (SF Symbols, Systemoptik inkl. Liquid Glass ab iOS 26).
 *
 * Vorher (PR #36) wurde `NativeTabBar` aus cmp-ios-chrome eingebettet: ein `UITabBarController` mit je einem
 * eigenen `ComposeUIViewController` pro Tab, ohne Größen-Modifier. Der Interop-Controller wurde 0×0 gemessen
 * (weißer Screen nach Onboarding/Login), die Tab-Kompositionen verloren alle CompositionLocals und
 * Tab-Wechsel kamen nie in [NavigationState] an.
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
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { IosTabBar(selected = selected, onSelect = onSelect) },
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

/** Standard-Höhe der UITabBar ohne Home-Indicator-Bereich (pt). */
private val TAB_BAR_HEIGHT = 49.dp

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun IosTabBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    // UITabBar hält den Delegate nur schwach – daher in der Komposition festhalten.
    val delegate = remember { TabBarDelegate { index -> MainTab.entries.getOrNull(index)?.let { currentOnSelect(it) } } }
    // Als State lesen, damit der `update`-Block per Snapshot-Beobachtung bei jeder Änderung erneut läuft.
    val selectedState = rememberUpdatedState(selected)
    val tintState = rememberUpdatedState(ChargeShareColors.Primary)
    val darkState = rememberUpdatedState(ChargeShareColors.IsDark)
    val bottomInset = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

    UIKitView(
        factory = {
            UITabBar().apply {
                val tabItems = MainTab.entries.mapIndexed { index, tab ->
                    UITabBarItem(
                        title = tab.title,
                        image = UIImage.systemImageNamed(tab.systemImage),
                        tag = index.toLong(),
                    )
                }
                setItems(tabItems, animated = false)
                this.delegate = delegate
            }
        },
        modifier = Modifier.fillMaxWidth().height(TAB_BAR_HEIGHT + bottomInset),
        update = { bar ->
            bar.tintColor = tintState.value.toUIColor()
            bar.overrideUserInterfaceStyle =
                if (darkState.value) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
            val target = bar.items?.getOrNull(selectedState.value.ordinal) as? UITabBarItem
            if (target != null && bar.selectedItem != target) bar.selectedItem = target
        },
    )
}

private class TabBarDelegate(private val onSelectIndex: (Int) -> Unit) : NSObject(), UITabBarDelegateProtocol {
    override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) {
        onSelectIndex(didSelectItem.tag.toInt())
    }
}

private fun Color.toUIColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())
