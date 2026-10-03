package app.chargeshare.server.util

import app.chargeshare.shared.model.ErrorCodes
import io.ktor.http.HttpStatusCode

/** Entspricht Go `apperror.AppError`: HTTP-Status + Code + Nachricht (`{"error": "...", "code": "..."}`). */
class AppException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

fun unauthorized(message: String) = AppException(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, message)
fun forbidden(message: String) = AppException(HttpStatusCode.Forbidden, ErrorCodes.FORBIDDEN, message)
fun notFound(message: String) = AppException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, message)
fun validationError(message: String) = AppException(HttpStatusCode.UnprocessableEntity, ErrorCodes.VALIDATION_ERROR, message)
fun conflict(message: String) = AppException(HttpStatusCode.Conflict, ErrorCodes.CONFLICT, message)
fun internal(message: String) = AppException(HttpStatusCode.InternalServerError, ErrorCodes.INTERNAL_ERROR, message)
fun badRequest(message: String) = AppException(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, message)
fun notImplemented(message: String) = AppException(HttpStatusCode.NotImplemented, ErrorCodes.NOT_IMPLEMENTED, message)
fun serviceUnavailable(message: String) = AppException(HttpStatusCode.ServiceUnavailable, ErrorCodes.INTERNAL_ERROR, message)
