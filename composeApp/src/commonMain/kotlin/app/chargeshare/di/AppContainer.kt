package app.chargeshare.di

import app.chargeshare.data.ApiAuthRepository
import app.chargeshare.data.ApiBookingRepository
import app.chargeshare.data.ApiFavoritesRepository
import app.chargeshare.data.ApiHostRepository
import app.chargeshare.data.ApiPaymentRepository
import app.chargeshare.data.ApiWallboxRepository
import app.chargeshare.data.AuthRepository
import app.chargeshare.data.BookingRepository
import app.chargeshare.data.FavoritesRepository
import app.chargeshare.data.HostRepository
import app.chargeshare.data.ImageLoader
import app.chargeshare.data.OsmTileSource
import app.chargeshare.data.PaymentRepository
import app.chargeshare.data.PayoutRepository
import app.chargeshare.data.ApiPayoutRepository
import app.chargeshare.data.DeepLinkInbox
import app.chargeshare.platform.ReceiptSharer
import app.chargeshare.platform.UnsupportedReceiptSharer
import app.chargeshare.platform.UnsupportedUrlOpener
import app.chargeshare.platform.UrlOpener
import app.chargeshare.data.PreferenceStorage
import app.chargeshare.data.WallboxRepository
import app.chargeshare.presentation.onboarding.OnboardingSettings
import app.chargeshare.presentation.theme.ThemeSettings
import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.api.TokenStore
import app.chargeshare.shared.api.createHttpClient
import io.ktor.client.plugins.HttpTimeout
import app.chargeshare.ui.map.WallboxMapHolder
import ovh.plrapps.mapcompose.core.TileStreamProvider

/**
 * Verdrahtet die Abhängigkeiten der App per Hand (kein DI-Framework): Http-Client → API → Repositories.
 * Wird einmal pro Prozess erzeugt (Android: `Application`, Desktop/Web: `main`).
 *
 * @param apiBaseUrl Server-Wurzel, z. B. `http://10.0.2.2:8080` (Android-Emulator) oder `http://localhost:8080`
 * @param tokenStore dauerhafter Token-Speicher der Plattform (`createSecureTokenStore`)
 * @param preferenceStorage dauerhafter Speicher für Einstellungen wie den Dunkelmodus (`createPreferenceStorage`)
 * @param showDebugInfo `true` nur in Debug-Builds: die UI zeigt dann Entwicklerangaben (z. B. die API-Adresse im Profil)
 * @param urlOpener öffnet Web-Adressen im Browser (Stripe-Onboarding); ohne Angabe nicht unterstützt
 * @param receiptSharer gibt Quittungs-PDFs aus (Teilen-Dialog, Download, Öffnen); ohne Angabe nicht unterstützt
 */
class AppContainer(
    val apiBaseUrl: String,
    tokenStore: TokenStore,
    val preferenceStorage: PreferenceStorage,
    val showDebugInfo: Boolean = false,
    val urlOpener: UrlOpener = UnsupportedUrlOpener,
    val receiptSharer: ReceiptSharer = UnsupportedReceiptSharer,
) {
    private val api = ChargeShareApi(
        http = createHttpClient(apiBaseUrl, tokenStore) {
            install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
        },
        tokenStore = tokenStore,
    )

    val authRepository: AuthRepository = ApiAuthRepository(api, tokenStore)
    val wallboxRepository: WallboxRepository = ApiWallboxRepository(api)
    val bookingRepository: BookingRepository = ApiBookingRepository(api)
    val hostRepository: HostRepository = ApiHostRepository(api)
    val favoritesRepository: FavoritesRepository = ApiFavoritesRepository(api)
    val paymentRepository: PaymentRepository = ApiPaymentRepository(api)
    val payoutRepository: PayoutRepository = ApiPayoutRepository(api)

    /** Briefkasten für Deep Links (`chargeshare://onboarding-done`), den die Plattform füllt und die UI abholt. */
    val deepLinks = DeepLinkInbox()

    /** Lädt Wallbox-Fotos (Liste, Karte, Detail) und hält die letzten im Speicher. */
    val imageLoader = ImageLoader(apiBaseUrl)

    /** Gewählte Darstellung (Hell/Dunkel/System), dauerhaft gespeichert. */
    val themeSettings = ThemeSettings(preferenceStorage)

    /** Einführung beim ersten Start; das „gesehen“-Flag liegt lokal im Einstellungsspeicher. */
    val onboarding = OnboardingSettings(preferenceStorage)

    /** Kachelquelle der Karte (OpenStreetMap); eigener Http-Client, ohne Login-Daten. */
    val tileSource: TileStreamProvider by lazy { OsmTileSource() }

    /**
     * Kartenzustand der Suche, einmal pro Prozess und nie beendet. Ein `shutdown()` beim Tab-Wechsel ließ die iOS-App
     * abstürzen (Details in [WallboxMapHolder]).
     */
    val wallboxMap: WallboxMapHolder by lazy { WallboxMapHolder(tileSource) }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
