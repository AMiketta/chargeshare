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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UITabBar
import platform.UIKit.UITabBarAppearance
import platform.UIKit.UITabBarDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObject

/**
 * Haupt-Tabs auf iOS, **edge-to-edge**: Der Tab-Inhalt füllt den ganzen Bildschirm, die echte UIKit-[UITabBar]
 * (SF Symbols, Systemmaterial bzw. Liquid Glass ab iOS 26) liegt als natives Overlay darüber und lässt Karte/Listen
 * durchscheinen. Wie weit sie den Inhalt überdeckt, bekommen die Tabs über [LocalTabBarOverlap] mit und polstern damit
 * scrollbare Inhalte und Bedienelemente.
 *
 * Der Tab-Inhalt bleibt in **derselben** Compose-Komposition (Theme, ImageLoader, nutzerbezogener ViewModel-Speicher,
 * Navigation). Vorher (PR #36) steckte jeder Tab in einem eigenen `ComposeUIViewController` eines 0×0 gemessenen
 * `UITabBarController` (weißer Screen, siehe PR #37).
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
    val bottomInset = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val barHeight = TAB_BAR_HEIGHT + bottomInset
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTabBarOverlap provides barHeight) {
            Box(Modifier.fillMaxSize()) {
                when (selected) {
                    MainTab.Search -> searchContent()
                    MainTab.Bookings -> bookingsContent()
                    MainTab.Host -> hostContent()
                    MainTab.Profile -> profileContent()
                }
            }
        }
        IosTabBar(
            selected = selected,
            onSelect = onSelect,
            height = barHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Standard-Höhe der UITabBar ohne Home-Indicator-Bereich (pt). */
private val TAB_BAR_HEIGHT = 49.dp

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun IosTabBar(selected: MainTab, onSelect: (MainTab) -> Unit, height: Dp, modifier: Modifier = Modifier) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    // UITabBar hält den Delegate nur schwach – daher in der Komposition festhalten.
    // UIKit ruft den Delegate auf dem Main-Thread und nur bei Nutzer-Tipps auf (nicht beim Setzen von selectedItem),
    // daher keine Rekursion mit dem update-Block.
    val delegate = remember { TabBarDelegate { index -> MainTab.entries.getOrNull(index)?.let { currentOnSelect(it) } } }
    // Als State lesen, damit der `update`-Block per Snapshot-Beobachtung bei jeder Änderung erneut läuft.
    val selectedState = rememberUpdatedState(selected)
    val tintState = rememberUpdatedState(ChargeShareColors.Primary)
    val darkState = rememberUpdatedState(ChargeShareColors.IsDark)

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
                // Immer das transluzente Systemmaterial zeigen (auch „am Scroll-Rand“, wo iOS 15–18 sonst
                // einen komplett durchsichtigen Hintergrund nimmt); ab iOS 26 übernimmt Liquid Glass.
                val appearance = UITabBarAppearance().apply { configureWithDefaultBackground() }
                standardAppearance = appearance
                scrollEdgeAppearance = appearance
                this.delegate = delegate
            }
        },
        modifier = modifier.fillMaxWidth().height(height),
        update = { bar ->
            bar.tintColor = tintState.value.toUIColor()
            bar.overrideUserInterfaceStyle =
                if (darkState.value) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
            val target = bar.items?.getOrNull(selectedState.value.ordinal) as? UITabBarItem
            if (target != null && bar.selectedItem != target) bar.selectedItem = target
        },
        // Natives Overlay über dem Compose-Canvas: der Tab-Inhalt darunter scheint durch das Material durch.
        // NonCooperative: Tipps gehen ohne Compose-Verzögerung direkt an die Leiste (kein Scroll-Konflikt in einer Tab-Leiste).
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
            placedAsOverlay = true,
        ),
    )
}

private class TabBarDelegate(private val onSelectIndex: (Int) -> Unit) : NSObject(), UITabBarDelegateProtocol {
    override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) {
        onSelectIndex(didSelectItem.tag.toInt())
    }
}

private fun Color.toUIColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())
