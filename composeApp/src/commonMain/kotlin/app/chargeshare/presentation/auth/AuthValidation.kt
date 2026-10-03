package app.chargeshare.presentation.auth

/** Fehler eines einzelnen Formularfelds; die UI übersetzt sie in deutsche Texte. */
enum class FieldError {
    Required,
    InvalidEmail,
    PasswordTooShort,
}

/** Mindestlänge laut Backend (`password: must be at least 8 characters`). */
const val MIN_PASSWORD_LENGTH = 8

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

fun validateName(name: String): FieldError? = if (name.isBlank()) FieldError.Required else null

fun validateEmail(email: String): FieldError? = when {
    email.isBlank() -> FieldError.Required
    !EMAIL_PATTERN.matches(email.trim()) -> FieldError.InvalidEmail
    else -> null
}

/** Beim Login genügt ein nicht leeres Passwort; die Mindestlänge gilt nur bei der Registrierung. */
fun validatePassword(password: String, enforceMinLength: Boolean): FieldError? = when {
    password.isEmpty() -> FieldError.Required
    enforceMinLength && password.length < MIN_PASSWORD_LENGTH -> FieldError.PasswordTooShort
    else -> null
}
