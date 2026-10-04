package app.chargeshare.server.services

import app.chargeshare.server.auth.AppleTokenVerifier
import app.chargeshare.server.auth.TokenService
import app.chargeshare.server.config.AppConfig
import app.chargeshare.server.db.ChargingSessions
import app.chargeshare.server.db.Bookings
import app.chargeshare.server.db.Db
import app.chargeshare.server.db.PgState
import app.chargeshare.server.db.RefreshTokens
import app.chargeshare.server.db.Reviews
import app.chargeshare.server.db.Users
import app.chargeshare.server.db.Wallboxes
import app.chargeshare.server.db.dbValue
import app.chargeshare.server.db.sqlState
import app.chargeshare.server.db.toUser
import app.chargeshare.server.db.toUserRole
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.util.EMAIL_REGEX
import app.chargeshare.server.util.conflict
import app.chargeshare.server.util.notFound
import app.chargeshare.server.util.unauthorized
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.AppleAuthRequest
import app.chargeshare.shared.model.AuthResponse
import app.chargeshare.shared.model.LoginRequest
import app.chargeshare.shared.model.RegisterRequest
import app.chargeshare.shared.model.TokenPair
import app.chargeshare.shared.model.UpdateRoleRequest
import app.chargeshare.shared.model.ProfileLimits
import app.chargeshare.shared.model.UpdateUserRequest
import app.chargeshare.shared.model.User
import app.chargeshare.shared.model.UserRole
import app.chargeshare.shared.model.UserStats
import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Auth- und Benutzerlogik (Go: `services/auth.go`, `handlers/auth.go`, `handlers/users.go`). */
class AuthService(
    private val db: Db,
    private val tokens: TokenService,
    private val config: AppConfig,
    private val payments: PaymentProvider,
    private val appleVerifier: AppleTokenVerifier,
    private val background: CoroutineScope,
) {
    private val random = SecureRandom()

    // ---- Passwort ---------------------------------------------------------------------------

    fun hashPassword(password: String): String =
        BCrypt.withDefaults().hashToString(config.bcryptCost, password.toCharArray())

    fun checkPassword(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    // ---- Registrierung / Login --------------------------------------------------------------

    suspend fun register(req: RegisterRequest): AuthResponse {
        val email = req.email.trim().lowercase()
        val name = req.name.trim()
        if (!EMAIL_REGEX.matches(email)) throw validationError("email: must be a valid email address")
        if (req.password.length < 8) throw validationError("password: must be at least 8 characters")
        if (name.isEmpty() || name.length > 100) throw validationError("name: must be 1-100 characters")

        val hash = hashPassword(req.password)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val userId = Uuid.random()
        try {
            db.tx {
                if (Users.selectAll().where { Users.email eq email }.any()) throw conflict("email already registered")
                Users.insert {
                    it[Users.id] = userId
                    it[Users.email] = email
                    it[passwordHash] = hash
                    it[Users.name] = name
                    it[role] = UserRole.DRIVER.dbValue()
                    it[isVerified] = false
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        } catch (e: Exception) {
            if (e.sqlState() == PgState.UNIQUE_VIOLATION) throw conflict("email already registered")
            throw e
        }

        // Stripe-Kunde best-effort im Hintergrund (Go: goroutine); der Fake-Provider liefert null.
        background.launch {
            runCatching {
                payments.createCustomer(email, name)?.let { cid ->
                    db.tx { Users.update({ Users.id eq userId }) { it[stripeCustomerId] = cid } }
                }
            }
        }
        return issue(loadUser(userId))
    }

    suspend fun login(req: LoginRequest): AuthResponse {
        val email = req.email.trim().lowercase()
        if (!EMAIL_REGEX.matches(email) || req.password.isEmpty()) throw validationError("email and password are required")
        val (user, hash) = db.tx {
            Users.selectAll().where { Users.email eq email }.singleOrNull()?.let { it.toUser() to it[Users.passwordHash] }
        } ?: throw unauthorized("invalid credentials")
        // Konstante Antwort fuer unbekannte Mail / falsches Passwort / Apple-only-Konto
        if (hash == null || !checkPassword(req.password, hash)) throw unauthorized("invalid credentials")
        return issue(user)
    }

    /**
     * Sign in with Apple. Benoetigt `APPLE_BUNDLE_ID` (Audience-Pruefung) und Netzzugriff auf Apples JWKS;
     * ohne Konfiguration antwortet der [AppleTokenVerifier] mit 501.
     */
    suspend fun loginWithApple(req: AppleAuthRequest): AuthResponse {
        if (req.identityToken.isBlank()) throw validationError("identity_token: is required")
        val apple = appleVerifier.verify(req.identityToken)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val user = db.tx {
            val bySub = Users.selectAll().where { Users.appleSub eq apple.sub }.singleOrNull()
            if (bySub != null) return@tx bySub.toUser()
            val email = (apple.email ?: "${apple.sub}@apple.chargeshare.invalid").lowercase()
            val byEmail = Users.selectAll().where { Users.email eq email }.singleOrNull()
            if (byEmail != null) {
                Users.update({ Users.id eq byEmail[Users.id] }) { it[appleSub] = apple.sub; it[updatedAt] = now }
                return@tx loadUserTx(byEmail[Users.id])
            }
            val id = Uuid.random()
            Users.insert {
                it[Users.id] = id
                it[Users.email] = email
                it[appleSub] = apple.sub
                it[name] = req.fullName?.takeIf { n -> n.isNotBlank() } ?: apple.email ?: "Apple User"
                it[role] = UserRole.DRIVER.dbValue()
                it[isVerified] = apple.emailVerified
                it[createdAt] = now
                it[updatedAt] = now
            }
            loadUserTx(id)
        }
        return issue(user)
    }

    // ---- Tokens -----------------------------------------------------------------------------

    private suspend fun issue(user: User): AuthResponse {
        val access = tokens.createAccessToken(user.id, user.role, email = user.email)
        val refresh = createRefreshToken(Uuid.parse(user.id))
        return AuthResponse(access, refresh, user)
    }

    private suspend fun createRefreshToken(userId: Uuid): String {
        val raw = ByteArray(32).also { random.nextBytes(it) }
        val token = Base64.getUrlEncoder().encodeToString(raw)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        db.tx {
            RefreshTokens.insert {
                it[id] = Uuid.random()
                it[RefreshTokens.userId] = userId
                it[tokenHash] = hashToken(token)
                it[expiresAt] = now.plusDays(config.refreshTokenTtlDays)
                it[createdAt] = now
            }
        }
        return token
    }

    /** Rotation: das alte Token wird atomar geloescht (Einmalverwendung), ein neues Paar ausgegeben. */
    suspend fun refresh(refreshToken: String): TokenPair {
        if (refreshToken.isBlank()) throw unauthorized("invalid refresh token")
        val hash = hashToken(refreshToken)
        val user = db.tx {
            val row = RefreshTokens.selectAll().where { RefreshTokens.tokenHash eq hash }.singleOrNull()
                ?: throw unauthorized("invalid refresh token")
            // einmalig einloesen: nur wer die Zeile wirklich geloescht hat, darf rotieren
            val deleted = RefreshTokens.deleteWhere { tokenHash eq hash }
            if (deleted == 0) throw unauthorized("invalid refresh token")
            if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(row[RefreshTokens.expiresAt])) throw unauthorized("refresh token expired")
            loadUserTx(row[RefreshTokens.userId])
        }
        val access = tokens.createAccessToken(user.id, user.role, email = user.email)
        return TokenPair(access, createRefreshToken(Uuid.parse(user.id)))
    }

    suspend fun logout(userId: Uuid, refreshToken: String) {
        val hash = hashToken(refreshToken)
        db.tx { RefreshTokens.deleteWhere { (tokenHash eq hash) and (RefreshTokens.userId eq userId) } }
    }

    // ---- Benutzer ---------------------------------------------------------------------------

    suspend fun loadUser(id: Uuid): User = db.tx { loadUserTx(id) }

    private fun org.jetbrains.exposed.v1.jdbc.JdbcTransaction.loadUserTx(id: Uuid): User =
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser() ?: throw notFound("user not found")

    /** Profil-Kennzahlen: Ladevorgänge und kWh als Fahrer, Bewertungen auf den eigenen Wallboxen als Host. */
    suspend fun stats(id: Uuid): UserStats = db.tx {
        loadUserTx(id)
        val completed = Bookings.select(Bookings.actualKwh)
            .where { (Bookings.driverId eq id) and (Bookings.status eq "completed") }
            .toList()
        val kwh = completed.sumOf { it[Bookings.actualKwh]?.toDouble() ?: 0.0 }
        val ratings = Reviews.select(Reviews.rating)
            .where { Reviews.wallboxId inSubQuery Wallboxes.select(Wallboxes.id).where { Wallboxes.ownerId eq id } }
            .map { it[Reviews.rating].toInt() }
        UserStats(
            sessions = completed.size,
            kwhCharged = roundTo(kwh, STATS_KWH_DECIMALS),
            ratingAvg = ratings.takeIf { it.isNotEmpty() }?.let { roundTo(it.average(), STATS_RATING_DECIMALS) },
            ratingCount = ratings.size,
        )
    }

    suspend fun updateProfile(id: Uuid, req: UpdateUserRequest): User {
        val name = req.name?.trim()
        if (name != null && (name.isEmpty() || name.length > ProfileLimits.MAX_NAME_LENGTH)) {
            throw validationError("name: must be 1-${ProfileLimits.MAX_NAME_LENGTH} characters")
        }
        // Leere Telefonnummer = Nummer entfernen
        val phone = req.phone?.trim()
        if (phone != null && phone.isNotEmpty() && !ProfileLimits.isValidPhone(phone)) {
            throw validationError("phone: must be a valid phone number")
        }
        req.avatarUrl?.let { if (it.isNotEmpty() && !(it.startsWith("http://") || it.startsWith("https://"))) throw validationError("avatar_url: must be a valid URL") }
        return db.tx {
            loadUserTx(id)
            Users.update({ Users.id eq id }) {
                name?.let { v -> it[Users.name] = v }
                if (phone != null) it[Users.phone] = phone.ifEmpty { null }
                req.avatarUrl?.let { v -> it[avatarUrl] = v }
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
            loadUserTx(id)
        }
    }

    suspend fun updateRole(id: Uuid, req: UpdateRoleRequest): User {
        // Wie Go (`oneof=driver host both`): admin kann nicht selbst gesetzt werden.
        if (req.role == UserRole.ADMIN) throw validationError("role: must be one of driver host both")
        return db.tx {
            loadUserTx(id)
            Users.update({ Users.id eq id }) {
                it[role] = req.role.dbValue()
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
            loadUserTx(id)
        }
    }

    /** Aktuelle Rolle aus der DB (Token-Claims koennen nach einer Rollenaenderung veraltet sein). */
    suspend fun currentRole(id: Uuid): UserRole =
        db.tx { Users.selectAll().where { Users.id eq id }.singleOrNull()?.get(Users.role)?.toUserRole() }
            ?: throw unauthorized("user no longer exists")

    /**
     * Loescht das Konto samt abhaengiger Daten. Das Go-Backend loescht nur die Zeile in `users`, was am
     * FK von bookings/reviews scheitert; hier werden Sitzungen, Bewertungen und Buchungen mitgeloescht.
     */
    suspend fun deleteAccount(id: Uuid) {
        db.tx {
            loadUserTx(id)
            val ownBoxes = Wallboxes.select(Wallboxes.id).where { Wallboxes.ownerId eq id }
            val affectedBookings = Bookings.select(Bookings.id)
                .where { (Bookings.driverId eq id) or (Bookings.wallboxId inSubQuery ownBoxes) }
            ChargingSessions.deleteWhere { bookingId inSubQuery affectedBookings }
            Reviews.deleteWhere { (authorId eq id) or (wallboxId inSubQuery ownBoxes) or (bookingId inSubQuery affectedBookings) }
            Bookings.deleteWhere { (driverId eq id) or (wallboxId inSubQuery ownBoxes) }
            Users.deleteWhere { Users.id eq id } // wallboxes + refresh_tokens per ON DELETE CASCADE
        }
    }

    private fun roundTo(value: Double, decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return Math.round(value * factor) / factor
    }

    companion object {
        private const val STATS_KWH_DECIMALS = 1
        private const val STATS_RATING_DECIMALS = 1

        fun hashToken(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
