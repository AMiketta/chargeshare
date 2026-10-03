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

@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val phone: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class UpdateRoleRequest(val role: UserRole)
