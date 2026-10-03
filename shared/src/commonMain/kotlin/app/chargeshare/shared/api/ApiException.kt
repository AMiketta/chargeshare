package app.chargeshare.shared.api

/** Fehler vom Backend (HTTP-Status + [app.chargeshare.shared.model.ApiError]). */
class ApiException(
    val status: Int,
    val code: String?,
    override val message: String,
) : Exception(message) {
    val isUnauthorized: Boolean get() = status == 401
    val isNotFound: Boolean get() = status == 404
}
