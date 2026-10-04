package app.chargeshare.server.services

import app.chargeshare.server.db.Db
import app.chargeshare.server.db.PasswordResetTokens
import app.chargeshare.server.db.RefreshTokens
import app.chargeshare.server.db.Users
import app.chargeshare.server.mail.MailMessage
import app.chargeshare.server.mail.Mailer
import app.chargeshare.server.util.EMAIL_REGEX
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.PasswordLimits
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * „Passwort vergessen“: Einmal-Token mit Ablauf, per E-Mail verschickt.
 *
 * - Die Anfrage antwortet immer gleich, egal ob es das Konto gibt (keine Auskunft über registrierte Adressen).
 * - Es gibt je Konto höchstens ein gültiges Token; eine neue Anfrage macht das alte ungültig.
 * - Nach dem Zurücksetzen sind alle Refresh-Tokens des Kontos widerrufen (alle Geräte müssen sich neu anmelden).
 */
class PasswordResetService(
    private val db: Db,
    private val auth: AuthService,
    private val mailer: Mailer,
    private val tokenTtl: Duration,
    private val publicBaseUrl: String?,
) {
    private val log = LoggerFactory.getLogger(PasswordResetService::class.java)
    private val random = SecureRandom()

    /**
     * Legt ein Token an und verschickt den Link. Unbekannte Adressen und Mail-Fehler bleiben für den Aufrufer unsichtbar.
     *
     * @param requestBaseUrl Adresse der Anfrage; der Link in der Mail zeigt auf `<Basis>/reset-password?token=…`, mit `PUBLIC_BASE_URL` auf diese
     */
    suspend fun request(email: String, requestBaseUrl: String) {
        val normalized = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(normalized)) return
        val (userId, name) = db.tx {
            Users.selectAll().where { Users.email eq normalized }.singleOrNull()?.let { it[Users.id] to it[Users.name] }
        } ?: return
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also { random.nextBytes(it) })
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        db.tx {
            PasswordResetTokens.deleteWhere { (PasswordResetTokens.userId eq userId) and (usedAt.isNull()) }
            PasswordResetTokens.insert {
                it[id] = Uuid.random()
                it[PasswordResetTokens.userId] = userId
                it[tokenHash] = AuthService.hashToken(token)
                it[expiresAt] = now.plusSeconds(tokenTtl.inWholeSeconds)
                it[createdAt] = now
            }
        }
        sendMail(normalized, name, "${(publicBaseUrl ?: requestBaseUrl).trimEnd('/')}/reset-password?token=$token")
    }

    /**
     * Setzt das Passwort mit einem gültigen Token neu.
     *
     * @throws app.chargeshare.server.util.AppException `422`, wenn Token ungültig, abgelaufen oder benutzt ist
     * oder das Passwort zu kurz ist
     */
    suspend fun reset(token: String, newPassword: String) {
        if (newPassword.length < PasswordLimits.MIN_LENGTH) {
            throw validationError("new_password: must be at least ${PasswordLimits.MIN_LENGTH} characters")
        }
        val hash = auth.hashPassword(newPassword)
        val tokenHash = AuthService.hashToken(token.trim())
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        db.tx {
            val row = PasswordResetTokens.selectAll().where { PasswordResetTokens.tokenHash eq tokenHash }.singleOrNull()
            if (row == null || row[PasswordResetTokens.usedAt] != null || now.isAfter(row[PasswordResetTokens.expiresAt])) {
                throw validationError(INVALID_TOKEN_MESSAGE)
            }
            // Einmalig einlösen: nur wer die Zeile wirklich als benutzt markiert, darf das Passwort setzen.
            val claimed = PasswordResetTokens.update({ (PasswordResetTokens.tokenHash eq tokenHash) and PasswordResetTokens.usedAt.isNull() }) {
                it[usedAt] = now
            }
            if (claimed == 0) throw validationError(INVALID_TOKEN_MESSAGE)
            val userId = row[PasswordResetTokens.userId]
            Users.update({ Users.id eq userId }) {
                it[passwordHash] = hash
                it[updatedAt] = now
            }
            RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
        }
    }

    private suspend fun sendMail(to: String, name: String, link: String) {
        val message = MailMessage(
            to = to,
            subject = "ChargeShare: Passwort zurücksetzen",
            body = "Hallo $name,\n\nüber diesen Link kannst du ein neues Passwort vergeben (gültig ${tokenTtl.inWholeMinutes} Minuten):\n\n$link\n\n" +
                "Wenn du das nicht angefordert hast, kannst du diese E-Mail ignorieren.\n\nDein ChargeShare-Team",
        )
        try {
            withContext(Dispatchers.IO) { mailer.send(message) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keine Auskunft an den Anfragenden: der Fehler wird nur protokolliert (ohne Adresse und ohne Link).
            log.error("sending password reset mail failed: {}", e.javaClass.simpleName)
        }
    }

    private companion object {
        const val TOKEN_BYTES = 32
        const val INVALID_TOKEN_MESSAGE = "token: invalid or expired"
    }
}
