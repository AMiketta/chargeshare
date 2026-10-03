package app.chargeshare.presentation.booking

import app.chargeshare.data.AppError
import app.chargeshare.payment.PaymentRequest
import app.chargeshare.shared.model.Booking

/**
 * Stand von „Jetzt bezahlen“ in der Buchungsliste. Es läuft höchstens eine Zahlung gleichzeitig.
 * Ablauf: `Preparing → PaymentRequired → InProgress(SHEET_OPEN) → InProgress(CONFIRMING) → Idle`.
 */
sealed interface PayNowState {
    data object Idle : PayNowState

    /** Zahlungskonfiguration und `client_secret` werden geholt. */
    data class Preparing(val bookingId: String) : PayNowState

    /** Die UI soll jetzt das Zahlungsfenster für [request] zeigen. */
    data class PaymentRequired(val booking: Booking, val request: PaymentRequest) : PayNowState

    data class InProgress(val booking: Booking, val request: PaymentRequest, val step: PaymentStep) : PayNowState

    /** Die Zahlung ist offen (abgebrochen, abgelehnt oder nicht bestätigt); der Fahrer kann es erneut versuchen. */
    data class Incomplete(val booking: Booking, val request: PaymentRequest, val issue: PaymentIssue) : PayNowState

    /** Das Bezahlen konnte gar nicht erst starten (Netz, Server). */
    data class PrepareFailed(val booking: Booking, val error: AppError) : PayNowState

    /** Auf diesem Gerät oder Server ist keine Zahlung möglich. */
    data class Unavailable(val booking: Booking, val reason: PaymentUnavailableReason) : PayNowState
}

/** Die Buchung, zu der der Zustand gehört (`null` bei [PayNowState.Idle]). */
val PayNowState.bookingId: String?
    get() = when (this) {
        PayNowState.Idle -> null
        is PayNowState.Preparing -> bookingId
        is PayNowState.PaymentRequired -> booking.id
        is PayNowState.InProgress -> booking.id
        is PayNowState.Incomplete -> booking.id
        is PayNowState.PrepareFailed -> booking.id
        is PayNowState.Unavailable -> booking.id
    }

/** Ein Bezahlvorgang ist gerade aktiv; weitere Aktionen (zweites Bezahlen, Storno) sind gesperrt. */
val PayNowState.isBusy: Boolean
    get() = this is PayNowState.Preparing || this is PayNowState.PaymentRequired || this is PayNowState.InProgress
