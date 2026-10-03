package app.chargeshare.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import app.chargeshare.presentation.host.HostDashboardViewModel
import app.chargeshare.presentation.profile.ProfileViewModel
import app.chargeshare.presentation.session.SessionViewModel
import app.chargeshare.presentation.wallbox.WallboxDetailViewModel
import app.chargeshare.presentation.wallbox.WallboxListViewModel
import app.chargeshare.shared.model.User
import app.chargeshare.ui.auth.AuthRoute
import app.chargeshare.ui.booking.BookingFlowRoute
import app.chargeshare.ui.booking.BookingsRoute
import app.chargeshare.ui.host.BecomeHostScreen
import app.chargeshare.ui.host.CreateWallboxRoute
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
    ChargeShareTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialThemeBackground) {
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

private val MaterialThemeBackground = ChargeShareColors.Background

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

    val screen = navigation.current
    if (screen != null) {
        // Jeder Vollbild-Screen hat einen eigenen ViewModel-Speicher und startet daher frisch.
        key(navigation.stack.size, screen) {
            val owner = rememberScopedViewModelStoreOwner()
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                FullScreen(screen, container, navigation, paymentLauncher)
            }
        }
        return
    }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = ChargeShareColors.Surface) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == navigation.tab,
                        onClick = { navigation.selectTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        TabContent(navigation.tab, container, user, navigation, Modifier.padding(padding))
    }
}

@Composable
private fun TabContent(tab: MainTab, container: AppContainer, user: User, navigation: NavigationState, modifier: Modifier) {
    when (tab) {
        MainTab.Search -> {
            val viewModel = viewModel(key = "search") { WallboxListViewModel(container.wallboxRepository) }
            WallboxListRoute(viewModel, onOpenWallbox = { navigation.push(Screen.WallboxDetail(it.id)) }, modifier = modifier)
        }
        MainTab.Bookings -> {
            val viewModel = viewModel(key = "bookings") { BookingsViewModel(container.bookingRepository) }
            BookingsRoute(viewModel, modifier = modifier)
        }
        MainTab.Host -> {
            if (user.isHost) {
                val viewModel = viewModel(key = "host") { HostDashboardViewModel(user.id, container.hostRepository) }
                HostDashboardRoute(viewModel, onAddWallbox = { navigation.push(Screen.CreateWallbox) }, modifier = modifier)
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
            ProfileRoute(user, viewModel, container.apiBaseUrl, modifier)
        }
    }
}

@Composable
private fun FullScreen(screen: Screen, container: AppContainer, navigation: NavigationState, paymentLauncher: PaymentLauncher) {
    when (screen) {
        is Screen.WallboxDetail -> {
            val viewModel = viewModel { WallboxDetailViewModel(screen.wallboxId, container.wallboxRepository) }
            WallboxDetailRoute(viewModel, onBack = { navigation.pop() }, onBook = { navigation.push(Screen.Booking(it)) })
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
    }
}
