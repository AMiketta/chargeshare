package app.chargeshare.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chargeshare.data.SessionState
import app.chargeshare.data.isHost
import app.chargeshare.di.AppContainer
import app.chargeshare.payment.PaymentLauncher
import app.chargeshare.payment.UnsupportedPaymentLauncher
import app.chargeshare.presentation.auth.AuthViewModel
import app.chargeshare.presentation.booking.BookingFlowViewModel
import app.chargeshare.presentation.booking.BookingsViewModel
import app.chargeshare.presentation.host.CreateWallboxViewModel
import app.chargeshare.presentation.host.EditWallboxViewModel
import app.chargeshare.presentation.host.HostDashboardViewModel
import app.chargeshare.presentation.profile.ProfileViewModel
import app.chargeshare.presentation.session.SessionViewModel
import app.chargeshare.presentation.wallbox.FavoritesViewModel
import app.chargeshare.presentation.wallbox.WallboxDetailViewModel
import app.chargeshare.platform.isMapSupported
import app.chargeshare.presentation.wallbox.WallboxListViewModel
import app.chargeshare.shared.model.User
import app.chargeshare.ui.auth.AuthRoute
import app.chargeshare.ui.booking.BookingFlowRoute
import app.chargeshare.ui.booking.BookingsRoute
import app.chargeshare.ui.components.AppNavigationBar
import app.chargeshare.ui.host.BecomeHostScreen
import app.chargeshare.ui.host.CreateWallboxRoute
import app.chargeshare.ui.host.EditWallboxRoute
import app.chargeshare.ui.host.HostDashboardRoute
import app.chargeshare.ui.profile.ProfileRoute
import app.chargeshare.ui.wallbox.WallboxDetailRoute
import app.chargeshare.ui.wallbox.WallboxListRoute

/**
 * Gemeinsamer UI-Einstieg für Android, iOS, Desktop und Web.
 *
 * @param container verdrahtete Abhängigkeiten (siehe [AppContainer]); wird einmal pro Prozess erzeugt
 * @param paymentLauncher Zahlungsfenster der Plattform (Google Pay / Apple Pay über Stripe); Desktop und Web
 * nutzen [UnsupportedPaymentLauncher]
 */
@Composable
fun App(container: AppContainer, paymentLauncher: PaymentLauncher = UnsupportedPaymentLauncher) {
    ChargeShareTheme(darkTheme = rememberIsDarkTheme(container.themeSettings)) {
        Surface(modifier = Modifier.fillMaxSize(), color = ChargeShareColors.Background) {
            val sessionViewModel = viewModel { SessionViewModel(container.authRepository) }
            val session by sessionViewModel.session.collectAsState()
            when (val current = session) {
                SessionState.Restoring -> RestoringSession()
                SessionState.SignedOut -> {
                    val authViewModel = viewModel { AuthViewModel(container.authRepository) }
                    AuthRoute(authViewModel)
                }
                // Pro Nutzer ein eigener ViewModel-Speicher: nach Abmelden/Konto-Wechsel bleiben keine Daten des Vorgängers übrig.
                is SessionState.SignedIn -> key(current.user.id) {
                    val owner = rememberScopedViewModelStoreOwner()
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        MainContent(container, current.user, paymentLauncher)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoringSession() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(Strings.SessionRestoring, modifier = Modifier.padding(top = Spacing.Medium))
        }
    }
}

/** Hauptnavigation für angemeldete Nutzer: Tabs plus Vollbild-Screens (Detail, Buchung, Wallbox anlegen). */
// BackHandler ist in CMP 1.12 als veraltet markiert (Nachfolger: NavigationEventHandler); Migration ist ein eigener Schritt.
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MainContent(container: AppContainer, user: User, paymentLauncher: PaymentLauncher) {
    val navigation = remember { NavigationState() }
    BackHandler(enabled = navigation.stack.isNotEmpty()) { navigation.pop() }

    // Ein Exemplar pro angemeldetem Nutzer, geteilt von Liste und Detailseite, damit das Herz überall gleich steht.
    val favorites = viewModel(key = "favorites") { FavoritesViewModel(container.favoritesRepository) }

    val screen = navigation.current
    if (screen != null) {
        // Jeder Vollbild-Screen hat einen eigenen ViewModel-Speicher und startet daher frisch.
        key(navigation.stack.size, screen) {
            val owner = rememberScopedViewModelStoreOwner()
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                FullScreen(screen, container, favorites, navigation, paymentLauncher)
            }
        }
        return
    }
    Scaffold(
        bottomBar = {
            AppNavigationBar(
                items = MainTab.entries,
                selected = navigation.tab,
                title = MainTab::title,
                icon = MainTab::icon,
                onSelect = navigation::selectTab,
            )
        },
    ) { padding ->
        TabContent(navigation.tab, container, user, favorites, navigation, paymentLauncher, Modifier.padding(padding))
    }
}

@Composable
private fun TabContent(
    tab: MainTab,
    container: AppContainer,
    user: User,
    favorites: FavoritesViewModel,
    navigation: NavigationState,
    paymentLauncher: PaymentLauncher,
    modifier: Modifier,
) {
    when (tab) {
        MainTab.Search -> {
            val viewModel = viewModel(key = "search") { WallboxListViewModel(container.wallboxRepository, mapSupported = isMapSupported) }
            WallboxListRoute(viewModel, favorites, container.tileSource, onOpenWallbox = { navigation.push(Screen.WallboxDetail(it.id)) }, modifier = modifier)
        }
        MainTab.Bookings -> {
            val viewModel = viewModel(key = "bookings") {
                BookingsViewModel(
                    repository = container.bookingRepository,
                    paymentRepository = container.paymentRepository,
                    supportedPaymentMethods = paymentLauncher.supportedMethods,
                )
            }
            BookingsRoute(viewModel, paymentLauncher, modifier = modifier)
        }
        MainTab.Host -> {
            if (user.isHost) {
                val viewModel = viewModel(key = "host") { HostDashboardViewModel(user.id, container.hostRepository) }
                HostDashboardRoute(
                    viewModel,
                    onAddWallbox = { navigation.push(Screen.CreateWallbox) },
                    onEditWallbox = { navigation.push(Screen.EditWallbox(it)) },
                    modifier = modifier,
                )
            } else {
                val profileViewModel = viewModel(key = "profile") { ProfileViewModel(container.authRepository) }
                val profileState by profileViewModel.state.collectAsState()
                BecomeHostScreen(
                    isBusy = profileState.isBusy,
                    errorMessage = profileState.error?.let(Strings::errorMessage),
                    onBecomeHost = profileViewModel::onBecomeHost,
                    modifier = modifier,
                )
            }
        }
        MainTab.Profile -> {
            val viewModel = viewModel(key = "profile") { ProfileViewModel(container.authRepository) }
            val themeMode by container.themeSettings.mode.collectAsState()
            ProfileRoute(
                user = user,
                viewModel = viewModel,
                themeMode = themeMode,
                onThemeModeChange = container.themeSettings::select,
                onOpenHost = { navigation.selectTab(MainTab.Host) },
                modifier = modifier,
                debugInfo = container.apiBaseUrl.takeIf { container.showDebugInfo },
            )
        }
    }
}

@Composable
private fun FullScreen(
    screen: Screen,
    container: AppContainer,
    favorites: FavoritesViewModel,
    navigation: NavigationState,
    paymentLauncher: PaymentLauncher,
) {
    when (screen) {
        is Screen.WallboxDetail -> {
            val viewModel = viewModel { WallboxDetailViewModel(screen.wallboxId, container.wallboxRepository) }
            WallboxDetailRoute(viewModel, favorites, onBack = { navigation.pop() }, onBook = { navigation.push(Screen.Booking(it)) })
        }
        is Screen.Booking -> {
            val viewModel = viewModel {
                BookingFlowViewModel(
                    wallbox = screen.wallbox,
                    wallboxRepository = container.wallboxRepository,
                    bookingRepository = container.bookingRepository,
                    paymentRepository = container.paymentRepository,
                    supportedPaymentMethods = paymentLauncher.supportedMethods,
                )
            }
            BookingFlowRoute(viewModel, paymentLauncher, onBack = { navigation.pop() }, onShowBookings = { navigation.popToTab(MainTab.Bookings) })
        }
        Screen.CreateWallbox -> {
            val viewModel = viewModel { CreateWallboxViewModel(container.hostRepository) }
            CreateWallboxRoute(viewModel, onBack = { navigation.pop() })
        }
        is Screen.EditWallbox -> {
            val viewModel = viewModel { EditWallboxViewModel(screen.wallbox, container.hostRepository) }
            EditWallboxRoute(viewModel, onBack = { navigation.pop() })
        }
    }
}
