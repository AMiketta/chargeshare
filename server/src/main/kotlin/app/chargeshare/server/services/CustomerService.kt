package app.chargeshare.server.services

import app.chargeshare.server.db.Db
import app.chargeshare.server.db.Users
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.util.notFound
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Stripe-Kunde eines Nutzers: wird bei der Registrierung best effort angelegt und hier bei Bedarf nachgeholt. */
class CustomerService(private val db: Db, private val payments: PaymentProvider) {

    /**
     * Liefert die Stripe-Kunden-ID von [userId] und legt den Kunden an, falls er fehlt (z. B. Stripe war bei der Registrierung
     * nicht erreichbar oder der Nutzer stammt aus dem Dev-Seed). `null` nur, wenn der Zahlungsanbieter keine Kunden kennt (Fake).
     */
    suspend fun ensure(userId: Uuid): String? {
        val (email, name, existing) = db.tx {
            val row = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: throw notFound("user not found")
            Triple(row[Users.email], row[Users.name], row[Users.stripeCustomerId])
        }
        if (!existing.isNullOrEmpty()) return existing
        val created = withContext(Dispatchers.IO) { payments.createCustomer(email, name) } ?: return null
        db.tx { Users.update({ Users.id eq userId }) { it[stripeCustomerId] = created } }
        return created
    }
}
