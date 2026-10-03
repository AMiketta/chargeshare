package app.chargeshare.presentation.booking

import app.chargeshare.data.AppError
import app.chargeshare.payment.PaymentMethodChoice
import app.chargeshare.shared.model.PaymentConfig

/** Warum auf diesem Gerät nicht bezahlt werden kann. */
enum class PaymentUnavailableReason {
    /** Der Server hat Stripe aktiviert, aber keinen Publishable Key geliefert. */
    SERVER_NOT_CONFIGURED,

    /** Desktop und Web haben kein Zahlungsfenster (weder Google Pay noch Apple Pay). */
    PLATFORM_NOT_SUPPORTED,
}

/** Was der Buchungsflow über die Zahlung weiß, bevor gebucht wird. */
sealed interface PaymentSetup {
    /** Konfiguration wird geladen. */
    data object Loading : PaymentSetup

    /** Entwicklungsmodus: der Server bucht ohne Zahlungsfenster, es wird nichts abgebucht. */
    data object TestPayment : PaymentSetup

    /** Zahlung per Stripe ist möglich; der Fahrer wählt unter [methods]. */
    data class Ready(val config: PaymentConfig, val methods: List<PaymentMethodChoice>) : PaymentSetup

    data class Unavailable(val reason: PaymentUnavailableReason) : PaymentSetup

    /** Die Konfiguration konnte nicht geladen werden (z. B. Netz weg). */
    data class LoadFailed(val error: AppError) : PaymentSetup
}

/** Warum die Zahlung einer angelegten Buchung noch offen ist. */
sealed interface PaymentIssue {
    /** Der Nutzer hat das Zahlungsfenster geschlossen. */
    data object Canceled : PaymentIssue

    /** Das Zahlungsfenster meldete einen Fehler (Karte abgelehnt, Wallet nicht verfügbar, …). */
    data object Failed : PaymentIssue

    /** Bezahlt wurde, aber der Server konnte die Zahlung noch nicht bestätigen. */
    data class ConfirmationFailed(val error: AppError) : PaymentIssue
}

/** Wo sich ein laufender Bezahlvorgang befindet. */
enum class PaymentStep {
    /** Das Zahlungsfenster ist geöffnet. */
    SHEET_OPEN,

    /** Zahlung ist erfolgt, der Server bestätigt die Buchung. */
    CONFIRMING,

    /** Die Buchung wird storniert, weil der Nutzer nicht mehr zahlen will. */
    DISCARDING,
}

/**
 * Leitet aus der Serverkonfiguration ab, wie auf diesem Gerät bezahlt werden kann.
 *
 * @param supportedMethods Zahlungsarten der Plattform ([app.chargeshare.payment.PaymentLauncher.supportedMethods])
 */
fun paymentSetupFor(config: PaymentConfig, supportedMethods: List<PaymentMethodChoice>): PaymentSetup {
    if (!config.requiresClientPayment) return PaymentSetup.TestPayment
    if (config.publishableKey.isNullOrBlank()) return PaymentSetup.Unavailable(PaymentUnavailableReason.SERVER_NOT_CONFIGURED)
    // Apple Pay braucht die Merchant-ID des Servers; ohne sie bleibt auf iOS nur die Kartenzahlung.
    val methods = supportedMethods.filter { it != PaymentMethodChoice.APPLE_PAY || config.applePayMerchantId != null }
    if (methods.isEmpty()) return PaymentSetup.Unavailable(PaymentUnavailableReason.PLATFORM_NOT_SUPPORTED)
    return PaymentSetup.Ready(config, methods)
}
