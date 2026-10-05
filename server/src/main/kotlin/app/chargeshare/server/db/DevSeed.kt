package app.chargeshare.server.db

import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.shared.model.UserRole
import at.favre.lib.crypto.bcrypt.BCrypt
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * Beispieldaten fuer die lokale Entwicklung (nur `ENV=development`, abschaltbar mit `DEV_SEED=false`).
 * Legt – idempotent – einen Demo-Host, einen Demo-Fahrer und einige Wallboxen in Bremen an.
 * Alle Eintraege sind als Beispieldaten gekennzeichnet (`[Beispiel]` im Namen, `@chargeshare.local`-Adressen).
 */
object DevSeed {
    const val HOST_EMAIL = "demo-host@chargeshare.local"
    const val DRIVER_EMAIL = "demo-driver@chargeshare.local"
    const val PASSWORD = "demo1234"
    private val log = LoggerFactory.getLogger(DevSeed::class.java)

    private data class Box(
        val name: String, val address: String, val postalCode: String, val lat: Double, val lng: Double,
        val powerKw: Double, val connector: ConnectorType, val price: Double,
    )

    private val boxes = listOf(
        Box("[Beispiel] Marktplatz", "Am Markt 1", "28195", 53.07586, 8.80777, 11.0, ConnectorType.TYPE2, 0.39),
        Box("[Beispiel] Hauptbahnhof", "Bahnhofsplatz 15", "28195", 53.08333, 8.81388, 22.0, ConnectorType.TYPE2, 0.45),
        Box("[Beispiel] Schnoor", "Wüstestätte 1", "28195", 53.07440, 8.80960, 11.0, ConnectorType.TYPE2, 0.35),
        Box("[Beispiel] Weserstadion", "Franz-Böhmert-Str. 1", "28205", 53.06640, 8.83760, 22.0, ConnectorType.CCS, 0.52),
        Box("[Beispiel] Universität", "Bibliothekstr. 1", "28359", 53.10670, 8.85170, 11.0, ConnectorType.TYPE2, 0.30),
        Box("[Beispiel] Überseestadt", "Konsul-Smidt-Str. 8", "28217", 53.10020, 8.77940, 50.0, ConnectorType.CCS, 0.62),
        Box("[Beispiel] Schwachhausen", "Schwachhauser Heerstr. 100", "28209", 53.08500, 8.84500, 3.7, ConnectorType.TYPE2, 0.28),
        Box("[Beispiel] Vegesack", "Weserterrassen 1", "28757", 53.16910, 8.62290, 11.0, ConnectorType.TYPE2, 0.37),
    )

    fun run(db: Db, bcryptCost: Int) {
        db.txBlocking {
            if (Users.selectAll().where { Users.email eq HOST_EMAIL }.any()) {
                // Vor V4: payouts_ready=false. Nach lokalem Stripe-Onboarding als Demo-Host: stripe_account_id ohne
                // Transfers-Capability → Destination Charges scheitern. Demo-Host bleibt ohne Connect buchbar.
                Users.update({ Users.email eq HOST_EMAIL }) {
                    it[payoutsReady] = true
                    it[stripeAccountId] = null
                }
                return@txBlocking
            }
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val hash = BCrypt.withDefaults().hashToString(bcryptCost, PASSWORD.toCharArray())
            val hostId = Uuid.random()
            Users.insert {
                it[id] = hostId; it[email] = HOST_EMAIL; it[passwordHash] = hash
                it[name] = "Demo Host (Beispieldaten)"; it[role] = UserRole.BOTH.dbValue()
                it[isVerified] = true; it[createdAt] = now; it[updatedAt] = now
                // Demo-Host braucht kein Stripe-Konto: seine Wallboxes bleiben auch mit echtem Stripe buchbar (ohne Auszahlung).
                it[payoutsReady] = true
            }
            Users.insert {
                it[id] = Uuid.random(); it[email] = DRIVER_EMAIL; it[passwordHash] = hash
                it[name] = "Demo Fahrer (Beispieldaten)"; it[role] = UserRole.DRIVER.dbValue()
                it[isVerified] = true; it[createdAt] = now; it[updatedAt] = now
            }
            // 06:00-22:00 UTC an allen Tagen (= 08:00-24:00 in Bremen im Sommer)
            val day = listOf(TimeSlot("06:00", "22:00"))
            val week = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday").associateWith { day }
            for (b in boxes) {
                Wallboxes.insert {
                    it[id] = Uuid.random(); it[ownerId] = hostId
                    it[name] = b.name; it[address] = b.address; it[city] = "Bremen"; it[postalCode] = b.postalCode
                    it[lat] = b.lat; it[lng] = b.lng
                    it[powerKw] = BigDecimal.valueOf(b.powerKw); it[connectorType] = b.connector.dbValue()
                    it[pricePerKwh] = BigDecimal.valueOf(b.price)
                    it[minDurationMin] = 30; it[maxDurationMin] = 480
                    it[isOnline] = true; it[isMetered] = false
                    it[model] = "Beispiel-Wallbox"; it[manufacturer] = "Beispieldaten"
                    it[photos] = emptyList(); it[availability] = week
                    it[ratingAvg] = BigDecimal.ZERO; it[ratingCount] = 0
                    it[createdAt] = now; it[updatedAt] = now
                }
            }
            log.warn("DEV-SEED: Beispieldaten angelegt ({} Wallboxen in Bremen; Login {} / {} bzw. {})", boxes.size, HOST_EMAIL, PASSWORD, DRIVER_EMAIL)
        }
    }
}
