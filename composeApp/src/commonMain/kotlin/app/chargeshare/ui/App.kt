package app.chargeshare.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.chargeshare.platform.LocationProvider
import app.chargeshare.platform.PhotoPicker
import app.chargeshare.platform.UnsupportedLocationProvider
import app.chargeshare.platform.UnsupportedPhotoPicker
import app.chargeshare.ui.components.LocalImageLoader
import app.chargeshare.payment.UnsupportedPaymentLauncher
import app.chargeshare.presentation.booking.BookingFlowViewModel
import app.chargeshare.presentation.booking.BookingsViewModel
import app.chargeshare.presentation.host.CreateWallboxViewModel
import app.chargeshare.presentation.host.EditWallboxViewModel
import app.chargeshare.data.DeepLink
import app.chargeshare.data.DeepLinkInbox
import app.chargeshare.presentation.host.HostDashboardViewModel
import app.chargeshare.presentation.host.PayoutViewModel
import app.chargeshare.presentation.profile.ProfileViewModel
import app.chargeshare.presentation.session.SessionViewModel
import app.chargeshare.presentation.wallbox.FavoritesViewModel
import app.chargeshare.presentation.wallbox.WallboxDetailViewModel
import app.chargeshare.platform.isMapSupported
import app.chargeshare.presentation.wallbox.WallboxListViewModel
import app.chargeshare.shared.model.User
import app.chargeshare.ui.booking.BookingFlowRoute
import app.chargeshare.ui.booking.BookingsRoute
import app.chargeshare.ui.host.BecomeHostScreen
import app.chargeshare.ui.host.CreateWallboxRoute
import app.chargeshare.ui.host.EditWallboxRoute
import app.chargeshare.ui.host.HostDashboardRoute
import app.chargeshare.presentation.profile.SavedCardsViewModel
import app.chargeshare.ui.onboarding.OnboardingScreen
import app.chargeshare.ui.profile.ProfileRoute
import app.chargeshare.ui.profile.SavedCardsRoute
import app.chargeshare.ui.wallbox.WallboxDetailRoute
import app.chargeshare.ui.wallbox.WallboxListRoute

/**
 * Gemeinsamer UI-Einstieg für Android, iOS, Desktop und Web.
 *
 * @param container verdrahtete Abhängigkeiten (siehe [AppContainer]); wird einmal pro Prozess erzeugt
 * @param paymentLauncher Zahlungsfenster der Plattform (Google Pay / Apple Pay über Stripe); Desktop und Web
 * nutzen [UnsupportedPaymentLauncher]
 * @param photoPicker Fotoauswahl der Plattform (Android: Photo Picker, Desktop: Dateidialog); sonst entfällt „Foto hinzufügen“
 * @param locationProvider Gerätestandort für die Suche (Android/iOS); Desktop/Web = [UnsupportedLocationProvider]
 */
@Composable
fun App(
    container: AppContainer,
    paymentLauncher: PaymentLauncher = UnsupportedPaymentLauncher,
    photoPicker: PhotoPicker = UnsupportedPhotoPicker,
    locationProvider: LocationProvider = UnsupportedLocationProvider,
) {
    val darkTheme = rememberIsDarkTheme(container.themeSettings)
    ChargeShareTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalImageLoader provides container.imageLoader) {
            Surface(modifier = Modifier.fillMaxSize(), color = ChargeShareColors.Background) {
                val sessionViewModel = viewModel { SessionViewModel(container.authRepository) }
                val session by sessionViewModel.session.collectAsState()
                LaunchedEffect(session) {
                    when (session) {
                        is SessionState.SignedIn -> container.pushDevices.syncRegistration()
                        SessionState.SignedOut -> container.pushDevices.unregisterLocal()
                        SessionState.Restoring -> Unit
                    }
                }
                var splashVisible by rememberSaveable { mutableStateOf(true) }
                Box {
                    when (val current = session) {
                        SessionState.Restoring -> RestoringSession()
                        SessionState.SignedOut -> SignedOutContent(container)
                        // Pro Nutzer ein eigener ViewModel-Speicher: nach Abmelden/Konto-Wechsel bleiben keine Daten des Vorgängers übrig.
                        is SessionState.SignedIn -> key(current.user.id) {
                            val owner = rememberScopedViewModelStoreOwner()
                            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                                MainContent(container, current.user, paymentLauncher, photoPicker, locationProvider)
                            }
                        }
                    }
                    val onboardingVisible by container.onboarding.visible.collectAsState()
                    if (onboardingVisible) {
                        OnboardingScreen(onFinished = container.onboarding::finish)
                    }
                    if (splashVisible) {
                        SplashOverlay(
                            isDark = darkTheme,
                            sessionRestoring = session == SessionState.Restoring,
                            onFinished = { splashVisible = false },
                        )
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
private fun MainContent(container: AppContainer, user: User, paymentLauncher: PaymentLauncher, photoPicker: PhotoPicker, locationProvider: LocationProvider) {
    val navigation = remember { NavigationState() }
    BackHandler(enabled = navigation.stack.isNotEmpty()) { navigation.pop() }

    // Ein Exemplar pro angemeldetem Nutzer, geteilt von Liste und Detailseite, damit das Herz überall gleich steht.
    val favorites = viewModel(key = "favorites") { FavoritesViewModel(container.favoritesRepository) }
    val payoutViewModel = viewModel(key = "payout") { PayoutViewModel(container.payoutRepository) }
    DeepLinkEffect(container.deepLinks, payoutViewModel, navigation)

    val screen = navigation.current
    if (screen != null) {
        // Jeder Vollbild-Screen hat einen eigenen ViewModel-Speicher und startet daher frisch.
        key(navigation.stack.size, screen) {
            val owner = rememberScopedViewModelStoreOwner()
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                FullScreen(screen, container, favorites, navigation, paymentLauncher, photoPicker)
            }
        }
        return
    }
    MainTabsChrome(
        selected = navigation.tab,
        onSelect = navigation::selectTab,
        searchContent = {
            TabContent(MainTab.Search, container, user, favorites, payoutViewModel, navigation, paymentLauncher, locationProvider, Modifier)
        },
        bookingsContent = {
            TabContent(MainTab.Bookings, container, user, favorites, payoutViewModel, navigation, paymentLauncher, locationProvider, Modifier)
        },
        hostContent = {
            TabContent(MainTab.Host, container, user, favorites, payoutViewModel, navigation, paymentLauncher, locationProvider, Modifier)
        },
        profileContent = {
            TabContent(MainTab.Profile, container, user, favorites, payoutViewModel, navigation, paymentLauncher, locationProvider, Modifier)
        },
    )
}

@Composable
private fun TabContent(
    tab: MainTab,
    container: AppContainer,
    user: User,
    favorites: FavoritesViewModel,
    payoutViewModel: PayoutViewModel,
    navigation: NavigationState,
    paymentLauncher: PaymentLauncher,
    locationProvider: LocationProvider,
    modifier: Modifier,
) {
    when (tab) {
        MainTab.Search -> {
            val viewModel = viewModel(key = "search") {
                WallboxListViewModel(
                    repository = container.wallboxRepository,
                    mapSupported = isMapSupported,
                    locationProvider = locationProvider,
                    preferences = container.preferenceStorage,
                )
            }
            WallboxListRoute(viewModel, favorites, container.wallboxMap, onOpenWallbox = { navigation.push(Screen.WallboxDetail(it.id)) }, modifier = modifier)
        }
        MainTab.Bookings -> {
            val viewModel = viewModel(key = "bookings") {
                BookingsViewModel(
                    repository = container.bookingRepository,
                    paymentRepository = container.paymentRepository,
                    supportedPaymentMethods = paymentLauncher.supportedMethods,
                )
            }
            BookingsRoute(viewModel, paymentLauncher, container.receiptSharer, modifier = modifier)
        }
        MainTab.Host -> {
            // Session erneut lesen: Host-Status kann sich ändern, während der Tab angezeigt wird.
            val session by container.authRepository.session.collectAsState()
            val hostUser = (session as? SessionState.SignedIn)?.user ?: user
            if (hostUser.isHost) {
                val viewModel = viewModel(key = "host") { HostDashboardViewModel(hostUser.id, container.hostRepository) }
                HostDashboardRoute(
                    viewModel,
                    payoutViewModel = payoutViewModel,
                    urlOpener = container.urlOpener,
                    userName = hostUser.name,
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
            val session by container.authRepository.session.collectAsState()
            val profileUser = (session as? SessionState.SignedIn)?.user ?: user
            val viewModel = viewModel(key = "profile") { ProfileViewModel(container.authRepository) }
            val themeMode by container.themeSettings.mode.collectAsState()
            ProfileRoute(
                user = profileUser,
                viewModel = viewModel,
                themeMode = themeMode,
                onThemeModeChange = container.themeSettings::select,
                onOpenHost = { navigation.selectTab(MainTab.Host) },
                onOpenPaymentMethods = { navigation.push(Screen.PaymentMethods) },
                onShowIntro = container.onboarding::show,
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
    photoPicker: PhotoPicker,
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
        Screen.PaymentMethods -> {
            val viewModel = viewModel { SavedCardsViewModel(container.paymentRepository, canAddCard = paymentLauncher.supportsCardSetup) }
            SavedCardsRoute(viewModel, paymentLauncher, onBack = { navigation.pop() })
        }
        Screen.CreateWallbox -> {
            val viewModel = viewModel { CreateWallboxViewModel(container.hostRepository) }
            CreateWallboxRoute(viewModel, onBack = { navigation.pop() })
        }
        is Screen.EditWallbox -> {
            val viewModel = viewModel { EditWallboxViewModel(screen.wallbox, container.hostRepository) }
            EditWallboxRoute(viewModel, photoPicker, onBack = { navigation.pop() })
        }
    }
}

/** Reagiert auf Deep Links des Stripe-Onboardings: zurück zum Host-Tab und Kontostatus sofort prüfen bzw. neuen Link holen. */
@Composable
private fun DeepLinkEffect(inbox: DeepLinkInbox, payoutViewModel: PayoutViewModel, navigation: NavigationState) {
    val link by inbox.pending.collectAsState()
    LaunchedEffect(link) {
        val current = link ?: return@LaunchedEffect
        if (current !is DeepLink.ResetPassword) navigation.selectTab(MainTab.Host)
        when (current) {
            DeepLink.OnboardingDone -> payoutViewModel.onReturnedFromOnboarding()
            DeepLink.OnboardingRefresh -> payoutViewModel.onOnboardingExpired()
            // Passwort-Links gelten nur abgemeldet (siehe SignedOutContent); angemeldet wird der Link verworfen.
            is DeepLink.ResetPassword -> Unit
        }
        inbox.consume(current)
    }
}
