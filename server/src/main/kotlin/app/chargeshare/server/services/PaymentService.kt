package app.chargeshare.server.services

import app.chargeshare.server.db.Db
import app.chargeshare.server.db.Users
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.util.notFound
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Stripe Connect Onboarding fuer Hosts (Go: `handlers/payments.go`). */
class PaymentService(private val db: Db, private val payments: PaymentProvider) {

    suspend fun onboardingUrl(userId: Uuid): String {
        val (email, existing) = db.tx {
            val u = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: throw notFound("user not found")
            u[Users.email] to u[Users.stripeAccountId]
        }
        val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { payments.createOnboardingLink(email, existing) }
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

    /** Nach dem Onboarding wird aus `driver` die Rolle `both` (wie im Go-Backend, ohne Stripe-Statuspruefung). */
    suspend fun onboardingReturn(userId: Uuid) {
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
}
