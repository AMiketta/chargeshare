package app.chargeshare.server.services

import app.chargeshare.server.db.Db
import app.chargeshare.server.db.PushDevices
import app.chargeshare.server.push.DevicePush
import app.chargeshare.server.push.PushNotifier
import app.chargeshare.server.util.badRequest
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.PushLimits
import app.chargeshare.shared.model.PushNotificationKind
import app.chargeshare.shared.model.PushPlatform
import app.chargeshare.shared.model.RegisterDeviceRequest
import app.chargeshare.shared.model.UnregisterDeviceRequest
import app.chargeshare.shared.model.toPayload
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Registrierung von Push-Tokens und Versand an die Geräte eines Nutzers. */
class PushDeviceService(
    private val db: Db,
    private val notifier: PushNotifier,
) {
    suspend fun register(userId: Uuid, req: RegisterDeviceRequest) {
        val token = req.token.trim()
        if (token.isEmpty()) throw validationError("token: must not be empty")
        if (token.length > PushLimits.TOKEN_MAX) {
            throw validationError("token: must be at most ${PushLimits.TOKEN_MAX} characters")
        }
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        db.tx {
            val existing = PushDevices.selectAll().where { PushDevices.token eq token }.singleOrNull()
            if (existing != null) {
                PushDevices.update({ PushDevices.token eq token }) {
                    it[PushDevices.userId] = userId
                    it[platform] = req.platform.dbValue()
                    it[updatedAt] = now
                }
            } else {
                PushDevices.insert {
                    it[id] = Uuid.random()
                    it[PushDevices.userId] = userId
                    it[platform] = req.platform.dbValue()
                    it[PushDevices.token] = token
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
    }

    suspend fun unregister(userId: Uuid, req: UnregisterDeviceRequest) {
        val token = req.token.trim()
        if (token.isEmpty()) throw badRequest("token must not be empty")
        db.tx {
            PushDevices.deleteWhere { (PushDevices.token eq token) and (PushDevices.userId eq userId) }
        }
    }

    /** Sendet [kind] an alle bekannten Geräte von [userId]. Ohne Token passiert nichts. */
    suspend fun notifyUser(userId: Uuid, kind: PushNotificationKind) {
        val payload = kind.toPayload()
        val devices = db.tx {
            PushDevices.selectAll().where { PushDevices.userId eq userId }.map { row ->
                DevicePush(
                    userId = userId,
                    platform = row[PushDevices.platform].toPushPlatform(),
                    token = row[PushDevices.token],
                    payload = payload,
                )
            }
        }
        if (devices.isNotEmpty()) notifier.send(devices)
    }
}

private fun PushPlatform.dbValue(): String = name.lowercase()

private fun String.toPushPlatform(): PushPlatform =
    PushPlatform.entries.first { it.name.equals(this, ignoreCase = true) }
