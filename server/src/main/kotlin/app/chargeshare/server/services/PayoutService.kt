package app.chargeshare.server.services

import app.chargeshare.server.db.Db
import app.chargeshare.server.db.Users
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.util.AppException
import app.chargeshare.server.util.notFound
import app.chargeshare.shared.model.ErrorCodes
import app.chargeshare.shared.model.PayoutState
import io.ktor.http.HttpStatusCode
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * Buchbarkeit nach Auszahlungskonto: Mit einem Zahlungsanbieter, der Connect-Konten verlangt ([PaymentProvider.requiresPayoutAccount]),
 * sind Wallboxes erst buchbar, wenn der Host das Onboarding abgeschlossen hat. Das Ergebnis steht in `users.payouts_ready`
 * und wird bei Stripe nachgefragt (beim Buchungsversuch, nach der Rueckkehr vom Onboarding und regelmaessig im Hintergrund).
 */
class PayoutService(private val db: Db, private val payments: PaymentProvider) {
    private val log = LoggerFactory.getLogger(PayoutService::class.java)

    /** IDs der Hosts aus [ownerIds], deren Wallboxes buchbar sind. Ohne Connect-Pflicht (Fake-Provider) sind es alle. */
    fun JdbcTransaction.readyOwners(ownerIds: Collection<String>): Set<String> {
        if (!payments.requiresPayoutAccount || ownerIds.isEmpty()) return ownerIds.toSet()
        val ids = ownerIds.map { Uuid.parse(it) }
        return Users.select(Users.id).where { (Users.id inList ids) and (Users.payoutsReady eq true) }.map { it[Users.id].toString() }.toSet()
    }

    /** Gespeicherter Stand des Auszahlungskontos von [userId] (ohne Stripe-Abfrage, siehe [refresh]). */
    suspend fun state(userId: Uuid): PayoutState {
        if (!payments.requiresPayoutAccount) return PayoutState.NOT_REQUIRED
        val row = db.tx { Users.select(Users.payoutsReady, Users.stripeAccountId).where { Users.id eq userId }.singleOrNull() }
            ?: throw notFound("user not found")
        return when {
            row[Users.payoutsReady] -> PayoutState.READY
            row[Users.stripeAccountId].isNullOrEmpty() -> PayoutState.NOT_STARTED
            else -> PayoutState.PENDING
        }
    }

    /** Wirft `409 HOST_PAYOUTS_PENDING`, wenn die Wallboxes des Hosts [ownerId] noch nicht buchbar sind. */
    suspend fun ensureBookable(ownerId: Uuid) {
        if (!payments.requiresPayoutAccount) return
        val (ready, accountId) = db.tx {
            val row = Users.select(Users.payoutsReady, Users.stripeAccountId).where { Users.id eq ownerId }.singleOrNull()
            (row?.get(Users.payoutsReady) ?: false) to row?.get(Users.stripeAccountId)
        }
        if (ready) return
        if (!accountId.isNullOrEmpty() && refresh(ownerId)) return
        throw pending()
    }

    /**
     * Fragt den Kontostatus bei Stripe ab und speichert ihn. Ein Fehler bei Stripe zaehlt als „noch nicht bereit“
     * (der alte Wert bleibt), damit Buchungsversuche nie in einem 500 enden.
     */
    suspend fun refresh(userId: Uuid): Boolean {
        val accountId = db.tx { Users.select(Users.stripeAccountId).where { Users.id eq userId }.singleOrNull()?.get(Users.stripeAccountId) }
        if (accountId.isNullOrEmpty()) return false
        val ready = try {
            withContext(Dispatchers.IO) { payments.isPayoutReady(accountId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("checking payout account of user {} failed: {}", userId, e.javaClass.simpleName)
            return false
        }
        if (ready) {
            db.tx {
                Users.update({ Users.id eq userId }) {
                    it[payoutsReady] = true
                    it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
        }
        return ready
    }

    /** Prueft alle Hosts mit Konto, aber ohne bestaetigtes Onboarding. @return Anzahl der neu freigeschalteten Hosts */
    suspend fun refreshPending(): Int {
        val pendingIds = db.tx {
            Users.select(Users.id).where { Users.stripeAccountId.isNotNull() and (Users.payoutsReady eq false) }.map { it[Users.id] }
        }
        return pendingIds.count { refresh(it) }
    }

    private fun pending() = AppException(HttpStatusCode.Conflict, ErrorCodes.HOST_PAYOUTS_PENDING, PENDING_MESSAGE)

    companion object {
        const val PENDING_MESSAGE =
            "Der Host richtet gerade seine Auszahlungen ein. Diese Wallbox ist in Kürze buchbar."
    }
}
