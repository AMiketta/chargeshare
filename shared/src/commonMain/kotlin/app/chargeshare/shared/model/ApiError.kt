package app.chargeshare.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fehlerantwort des Backends: `{"error": "...", "code": "NOT_FOUND"}` (Go: `apperror.AppError`). */
@Serializable
data class ApiError(
    @SerialName("error") val message: String,
    @SerialName("code") val code: String? = null,
)

/** Fehlercodes des Backends (Go: `pkg/apperror`). */
object ErrorCodes {
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val CONFLICT = "CONFLICT"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val PAYMENT_REQUIRED = "PAYMENT_REQUIRED"
    const val NOT_IMPLEMENTED = "NOT_IMPLEMENTED"
}
