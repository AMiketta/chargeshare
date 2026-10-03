package app.chargeshare.server.db

import app.chargeshare.shared.model.WeeklyAvailability
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

/**
 * Exposed-Tabellen passend zu `db/migration/V1__init.sql` (Schema 1:1 aus dem Go-Backend uebernommen).
 * Das Schema selbst wird von Flyway angelegt; diese Objekte dienen nur dem typsicheren Zugriff.
 */
object Users : Table("users") {
    val id = uuid("id")
    val email = text("email")
    val passwordHash = text("password_hash").nullable()
    val appleSub = text("apple_sub").nullable()
    val name = text("name")
    val phone = text("phone").nullable()
    val role = text("role")
    val stripeCustomerId = text("stripe_customer_id").nullable()
    val stripeAccountId = text("stripe_account_id").nullable()
    val avatarUrl = text("avatar_url").nullable()
    val isVerified = bool("is_verified")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Wallboxes : Table("wallboxes") {
    val id = uuid("id")
    val ownerId = uuid("owner_id").references(Users.id)
    val name = text("name")
    val address = text("address")
    val city = text("city")
    val postalCode = text("postal_code")
    val lat = double("lat")
    val lng = double("lng")
    val powerKw = decimal("power_kw", 5, 2)
    val connectorType = text("connector_type")
    val pricePerKwh = decimal("price_per_kwh", 6, 4)
    val minDurationMin = integer("min_duration_min")
    val maxDurationMin = integer("max_duration_min")
    val isOnline = bool("is_online")
    val isMetered = bool("is_metered")
    val ocppId = text("ocpp_id").nullable()
    val model = text("model").nullable()
    val manufacturer = text("manufacturer").nullable()
    val description = text("description").nullable()
    val photos = array<String>("photos").nullable()
    val availability = jsonb<WeeklyAvailability>("availability", Json.Default)
    val ratingAvg = decimal("rating_avg", 3, 2).nullable()
    val ratingCount = integer("rating_count").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Bookings : Table("bookings") {
    val id = uuid("id")
    val wallboxId = uuid("wallbox_id").references(Wallboxes.id)
    val driverId = uuid("driver_id").references(Users.id)
    val startTime = timestampWithTimeZone("start_time")
    val endTime = timestampWithTimeZone("end_time")
    val status = text("status")
    val estimatedKwh = decimal("estimated_kwh", 8, 3).nullable()
    val actualKwh = decimal("actual_kwh", 8, 3).nullable()
    val pricePerKwh = decimal("price_per_kwh", 6, 4)
    val totalAmount = decimal("total_amount", 10, 2).nullable()
    val stripePaymentIntentId = text("stripe_payment_intent_id").nullable()
    val stripeChargeId = text("stripe_charge_id").nullable()
    val ocppTransactionId = integer("ocpp_transaction_id").nullable()
    val cancelReason = text("cancel_reason").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Reviews : Table("reviews") {
    val id = uuid("id")
    val bookingId = uuid("booking_id").references(Bookings.id)
    val wallboxId = uuid("wallbox_id").references(Wallboxes.id)
    val authorId = uuid("author_id").references(Users.id)
    val rating = short("rating")
    val comment = text("comment").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ChargingSessions : Table("charging_sessions") {
    val id = uuid("id")
    val bookingId = uuid("booking_id").references(Bookings.id).nullable()
    val ocppId = text("ocpp_id")
    val transactionId = integer("transaction_id").nullable()
    val meterStart = integer("meter_start").nullable()
    val meterStop = integer("meter_stop").nullable()
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val stoppedAt = timestampWithTimeZone("stopped_at").nullable()
    val stopReason = text("stop_reason").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val tokenHash = text("token_hash")
    val expiresAt = timestampWithTimeZone("expires_at")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}
