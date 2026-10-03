package app.chargeshare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import app.chargeshare.shared.model.Wallbox

/** Tabs der Hauptnavigation. */
enum class MainTab(val title: String, val icon: ImageVector) {
    Search(Strings.TabSearch, AppIcons.Map),
    Bookings(Strings.TabBookings, AppIcons.List),
    Host(Strings.TabHost, AppIcons.Bolt),
    Profile(Strings.TabProfile, AppIcons.Person),
}

/** Vollbild-Screens oberhalb der Tabs. */
sealed interface Screen {
    data class WallboxDetail(val wallboxId: String) : Screen
    data class Booking(val wallbox: Wallbox) : Screen
    data object CreateWallbox : Screen
}

/** Zustandsbasierte Navigation: ein gewählter Tab plus ein Stapel von Vollbild-Screens. */
@Stable
class NavigationState {
    var tab: MainTab by mutableStateOf(MainTab.Search)
        private set
    val stack = mutableStateListOf<Screen>()

    val current: Screen? get() = stack.lastOrNull()

    fun selectTab(tab: MainTab) {
        stack.clear()
        this.tab = tab
    }

    fun push(screen: Screen) {
        stack.add(screen)
    }

    /** Geht einen Schritt zurück; `false`, wenn man schon auf einem Tab-Screen ist. */
    fun pop(): Boolean = stack.removeLastOrNull() != null

    /** Schließt alle Vollbild-Screens und wechselt zu [tab]. */
    fun popToTab(tab: MainTab) = selectTab(tab)
}

/**
 * Eigener [ViewModelStoreOwner], dessen ViewModels mit dem Verlassen der Komposition entsorgt werden.
 * So haben Detail-Screens frische ViewModels, und nach dem Abmelden bleiben keine Daten des alten Nutzers im Speicher.
 */
@Composable
fun rememberScopedViewModelStoreOwner(): ViewModelStoreOwner {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    return owner
}
