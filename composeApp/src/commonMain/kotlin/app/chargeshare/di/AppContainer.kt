package app.chargeshare.di

import app.chargeshare.data.ApiAuthRepository
import app.chargeshare.data.ApiBookingRepository
import app.chargeshare.data.ApiHostRepository
import app.chargeshare.data.ApiPaymentRepository
import app.chargeshare.data.ApiWallboxRepository
import app.chargeshare.data.AuthRepository
import app.chargeshare.data.BookingRepository
import app.chargeshare.data.HostRepository
import app.chargeshare.data.PaymentRepository
import app.chargeshare.data.WallboxRepository
import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.api.TokenStore
import app.chargeshare.shared.api.createHttpClient
import io.ktor.client.plugins.HttpTimeout

/**
 * Verdrahtet die Abhängigkeiten der App per Hand (kein DI-Framework): Http-Client → API → Repositories.
 * Wird einmal pro Prozess erzeugt (Android: `Application`, Desktop/Web: `main`).
 *
 * @param apiBaseUrl Server-Wurzel, z. B. `http://10.0.2.2:8080` (Android-Emulator) oder `http://localhost:8080`
 * @param tokenStore dauerhafter Token-Speicher der Plattform (`createSecureTokenStore`)
 */
class AppContainer(
    val apiBaseUrl: String,
    tokenStore: TokenStore,
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
    val paymentRepository: PaymentRepository = ApiPaymentRepository(api)

    private companion object {
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
