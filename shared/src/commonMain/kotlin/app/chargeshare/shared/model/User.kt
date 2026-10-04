package app.chargeshare.shared.model

import app.chargeshare.shared.util.InstantAsStringSerializer
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    @SerialName("driver") DRIVER,
    @SerialName("host") HOST,
    @SerialName("both") BOTH,
    @SerialName("admin") ADMIN,
}

/** Go: `models.User` (password_hash und apple_sub werden nie ausgeliefert). */
@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val phone: String? = null,
    val role: UserRole = UserRole.DRIVER,
    @SerialName("stripe_customer_id") val stripeCustomerId: String? = null,
    @SerialName("stripe_account_id") val stripeAccountId: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("created_at") @Serializable(InstantAsStringSerializer::class) val createdAt: Instant,
    @SerialName("updated_at") @Serializable(InstantAsStringSerializer::class) val updatedAt: Instant,
)

// ---- Auth-DTOs ------------------------------------------------------------------------------

@Serializable
data class RegisterRequest(val email: String, val password: String, val name: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AppleAuthRequest(
    @SerialName("identity_token") val identityToken: String,
    @SerialName("full_name") val fullName: String? = null,
)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class LogoutRequest(@SerialName("refresh_token") val refreshToken: String)

/** Antwort von register / login / apple. */
@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val user: User? = null,
)

/** Antwort von `POST /auth/refresh` (Go liefert hier nur die beiden Tokens, ohne `user`). */
@Serializable
data class TokenPair(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)

/** Grenzen der Profilfelder; Server und Apps prüfen dieselben Werte. */
object ProfileLimits {
    const val MAX_NAME_LENGTH = 100
    const val MIN_PHONE_DIGITS = 6
    const val MAX_PHONE_LENGTH = 20

    /** Erlaubte Zeichen einer Telefonnummer: Ziffern, `+` am Anfang, Leerzeichen, `-`, `/`, Klammern. */
    val PHONE_PATTERN: Regex = Regex("""^\+?[0-9 ()/\-]+$""")

    /** `true`, wenn [phone] (bereits getrimmt) eine plausible Telefonnummer ist. */
    fun isValidPhone(phone: String): Boolean =
        phone.length <= MAX_PHONE_LENGTH && PHONE_PATTERN.matches(phone) && phone.count { it.isDigit() } >= MIN_PHONE_DIGITS
}

/**
 * `PUT /users/me`: nur gesetzte Felder ändern sich. Ein leerer [phone] entfernt die Nummer.
 */
@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val phone: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/**
 * Kennzahlen für das Profil (`GET /users/me/stats`).
 *
 * @property sessions abgeschlossene Ladevorgänge als Fahrer (Buchungen mit Status `completed`)
 * @property kwhCharged Summe der tatsächlich geladenen kWh dieser Buchungen
 * @property ratingAvg Durchschnitt der Bewertungen auf den eigenen Wallboxen; `null`, solange es keine gibt
 * @property ratingCount Anzahl dieser Bewertungen
 */
@Serializable
data class UserStats(
    val sessions: Int = 0,
    @SerialName("kwh_charged") val kwhCharged: Double = 0.0,
    @SerialName("rating_avg") val ratingAvg: Double? = null,
    @SerialName("rating_count") val ratingCount: Int = 0,
)

@Serializable
data class UpdateRoleRequest(val role: UserRole)
