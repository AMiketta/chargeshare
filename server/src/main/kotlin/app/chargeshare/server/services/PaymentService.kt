package app.chargeshare.server.services

import app.chargeshare.server.db.Db
import app.chargeshare.server.db.Users
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.util.AppException
import app.chargeshare.server.util.badGateway
import app.chargeshare.server.util.notFound
import app.chargeshare.server.util.serviceUnavailable
import com.stripe.exception.ApiConnectionException
import com.stripe.exception.AuthenticationException
import com.stripe.exception.RateLimitException
import com.stripe.exception.StripeException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/** Stripe Connect Onboarding fuer Hosts (Go: `handlers/payments.go`). */
class PaymentService(private val db: Db, private val payments: PaymentProvider, private val payouts: PayoutService) {
    private val log = LoggerFactory.getLogger(PaymentService::class.java)

    suspend fun onboardingUrl(userId: Uuid): String {
        val (email, existing) = db.tx {
            val u = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: throw notFound("user not found")
            u[Users.email] to u[Users.stripeAccountId]
        }
        val info = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { payments.createOnboardingLink(email, existing) }
        } catch (e: StripeException) {
            throw onboardingFailure(e)
        }
        if (info.accountId != existing) {
            db.tx {
                Users.update({ Users.id eq userId }) {
                    it[stripeAccountId] = info.accountId
                    it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
        }
        return info.url
    }

    /** Nach dem Onboarding wird aus `driver` die Rolle `both` (wie im Go-Backend); zusaetzlich wird der Kontostatus bei Stripe geprueft ([PayoutService.refresh]). */
    suspend fun onboardingReturn(userId: Uuid) {
        payouts.refresh(userId)
        db.tx {
            val current = Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.role) ?: throw notFound("user not found")
            if (current == "driver") {
                Users.update({ Users.id eq userId }) {
                    it[role] = "both"
                    it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
        }
    }

    /**
     * Uebersetzt einen Stripe-Fehler in eine lesbare, deutsche Meldung. Der Rohtext von Stripe wird bewusst nicht
     * weitergereicht (kann Konto-/Schluesselhinweise enthalten) und auch nicht geloggt: protokolliert werden nur
     * Fehlerklasse, Code, HTTP-Status und Request-ID.
     */
    private fun onboardingFailure(e: StripeException): AppException {
        log.warn(
            "stripe onboarding failed: {} code={} status={} requestId={}",
            e.javaClass.simpleName, e.code, e.statusCode, e.requestId,
        )
        return when {
            e is AuthenticationException -> badGateway("Die Zahlungsanbindung ist nicht korrekt eingerichtet. Bitte später erneut versuchen.")
            e is ApiConnectionException || e is RateLimitException ->
                serviceUnavailable("Der Zahlungsanbieter ist gerade nicht erreichbar. Bitte später erneut versuchen.")
            CONNECT_NOT_ENABLED_HINT in e.message.orEmpty() ->
                serviceUnavailable("Stripe Connect ist für dieses Konto nicht aktiviert. Auszahlungen sind noch nicht möglich.")
            else -> badGateway("Das Auszahlungskonto konnte nicht eingerichtet werden. Bitte später erneut versuchen.")
        }
    }

    private companion object {
        /** Teil der Stripe-Fehlermeldung „You can only create new accounts if you've signed up for Connect“. */
        const val CONNECT_NOT_ENABLED_HINT = "signed up for Connect"
    }
}
