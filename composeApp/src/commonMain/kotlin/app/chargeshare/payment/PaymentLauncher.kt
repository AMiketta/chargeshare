package app.chargeshare.payment

import app.chargeshare.shared.model.PaymentConfig
import kotlin.coroutines.cancellation.CancellationException

/** Zahlungsarten, die der Fahrer im Buchungsflow wählen kann. */
enum class PaymentMethodChoice {
    /** Google Pay über das Stripe-PaymentSheet (nur Android). */
    GOOGLE_PAY,

    /** Apple Pay über das Stripe-PaymentSheet (nur iOS, braucht eine Apple-Merchant-ID). */
    APPLE_PAY,

    /** Kreditkarte und weitere Zahlungsarten des PaymentSheets, ohne Wallet-Schaltfläche. */
    CARD,
}

/**
 * Alles, was die Plattform braucht, um die Zahlung für eine bereits angelegte Buchung anzuzeigen.
 *
 * @property clientSecret `client_secret` des PaymentIntents aus der Antwort von `POST /bookings`
 * @property config öffentliche Server-Konfiguration (Publishable Key, Händlerdaten)
 * @property method gewählte Zahlungsart; [PaymentMethodChoice.CARD] blendet die Wallet aus
 */
data class PaymentRequest(
    val clientSecret: String,
    val config: PaymentConfig,
    val method: PaymentMethodChoice,
)

/**
 * Alles, was die Plattform braucht, um eine Karte ohne Zahlung zu speichern (Stripe-SetupIntent).
 *
 * @property clientSecret `client_secret` des SetupIntents aus `POST /payments/setup-intent`
 */
data class SaveCardRequest(val clientSecret: String, val config: PaymentConfig)

/** Ausgang einer Zahlung im plattformeigenen Zahlungsfenster. */
sealed interface PaymentResult {
    /** Stripe meldet die Zahlung als abgeschlossen; der Server prüft sie danach noch einmal (`confirm-payment`). */
    data object Completed : PaymentResult

    /** Der Nutzer hat das Zahlungsfenster geschlossen. Es wurde nichts abgebucht. */
    data object Canceled : PaymentResult

    /**
     * Die Zahlung ist gescheitert (Karte abgelehnt, Wallet nicht verfügbar, SDK-Fehler).
     *
     * @property reason technische Ursache nur für Logs; wird dem Nutzer nie angezeigt
     */
    data class Failed(val reason: String?) : PaymentResult
}

/**
 * Plattformneutrale Schnittstelle zum Zahlungsfenster (Stripe PaymentSheet mit Google Pay bzw. Apple Pay).
 *
 * Implementierungen: Android (`androidApp`, Stripe-Android-SDK), iOS (Swift-Brücke in `iosApp`, Stripe-iOS-SDK)
 * und [UnsupportedPaymentLauncher] für Desktop und Web.
 */
interface PaymentLauncher {
    /** Zahlungsarten dieser Plattform in Anzeigereihenfolge; leer, wenn hier keine Zahlung möglich ist. */
    val supportedMethods: List<PaymentMethodChoice>

    /**
     * Zeigt das Zahlungsfenster und wartet auf das Ergebnis.
     * Erwartbare Ausgänge (Abbruch, Ablehnung) kommen als [PaymentResult], nicht als Exception.
     */
    suspend fun pay(request: PaymentRequest): PaymentResult

    /** Kann diese Plattform eine Karte ohne Zahlung speichern ([saveCard])? Sonst bleibt „Karte hinzufügen“ ausgeblendet. */
    val supportsCardSetup: Boolean get() = false

    /**
     * Zeigt das Kartenformular von Stripe und speichert die Karte für den Nutzer (SetupIntent).
     * [PaymentResult.Completed] bedeutet: die Karte ist gespeichert.
     */
    suspend fun saveCard(request: SaveCardRequest): PaymentResult =
        PaymentResult.Failed(reason = "Karten speichern wird auf dieser Plattform nicht unterstützt")
}

/** Für Desktop und Web: dort gibt es weder Google Pay noch Apple Pay über Stripe (siehe README, „Zahlung“). */
object UnsupportedPaymentLauncher : PaymentLauncher {
    override val supportedMethods: List<PaymentMethodChoice> = emptyList()

    override suspend fun pay(request: PaymentRequest): PaymentResult =
        PaymentResult.Failed(reason = "Zahlungsfenster wird auf dieser Plattform nicht unterstützt")
}

/** Wie [payCatching], für [PaymentLauncher.saveCard]. */
suspend fun PaymentLauncher.saveCardCatching(request: SaveCardRequest): PaymentResult =
    try {
        saveCard(request)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PaymentResult.Failed(reason = e.message)
    }

/**
 * Wie [PaymentLauncher.pay], übersetzt aber unerwartete Ausnahmen des Plattform-SDKs in [PaymentResult.Failed],
 * damit der Buchungsflow nie in „Zahlung läuft“ hängen bleibt. [CancellationException] wird weitergeworfen.
 */
suspend fun PaymentLauncher.payCatching(request: PaymentRequest): PaymentResult =
    try {
        pay(request)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PaymentResult.Failed(reason = e.message)
    }
