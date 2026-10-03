package app.chargeshare.presentation.booking

import app.chargeshare.data.AppError
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.ReviewLimits

/** Nur abgeschlossene, noch nicht bewertete Buchungen lassen sich bewerten. */
fun Booking.isReviewable(): Boolean = status == BookingStatus.COMPLETED && !reviewed

/**
 * Eingabe im Bewertungsdialog.
 *
 * @property rating Sterne; `0` = noch nichts gewählt
 * @property error Fehler des letzten Sendeversuchs
 */
data class ReviewDraft(
    val booking: Booking,
    val rating: Int = 0,
    val comment: String = "",
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
) {
    val isCommentTooLong: Boolean get() = comment.trim().length > ReviewLimits.MAX_COMMENT_LENGTH

    val canSubmit: Boolean
        get() = rating in ReviewLimits.MIN_RATING..ReviewLimits.MAX_RATING && !isCommentTooLong && !isSubmitting
}
