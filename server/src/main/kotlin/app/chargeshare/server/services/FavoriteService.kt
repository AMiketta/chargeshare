package app.chargeshare.server.services

import app.chargeshare.server.db.Db
import app.chargeshare.server.db.Favorites
import app.chargeshare.server.db.Wallboxes
import app.chargeshare.server.util.notFound
import app.chargeshare.shared.model.FavoritesResponse
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Favoriten (Herz) eines Nutzers: Wallboxen, die er sich merkt. Alle Aufrufe sind idempotent. */
class FavoriteService(private val db: Db) {

    /** IDs der Favoriten von [userId], neueste zuerst. */
    suspend fun list(userId: Uuid): FavoritesResponse = db.tx {
        val ids = Favorites.selectAll()
            .where { Favorites.userId eq userId }
            .orderBy(Favorites.createdAt to SortOrder.DESC)
            .map { it[Favorites.wallboxId].toString() }
        FavoritesResponse(ids)
    }

    /** Merkt [wallboxId] für [userId]; ein zweites Mal ist wirkungslos. 404, wenn die Wallbox nicht existiert. */
    suspend fun add(userId: Uuid, wallboxId: Uuid) {
        db.tx {
            if (Wallboxes.select(Wallboxes.id).where { Wallboxes.id eq wallboxId }.empty()) throw notFound("wallbox not found")
            Favorites.insertIgnore {
                it[Favorites.userId] = userId
                it[Favorites.wallboxId] = wallboxId
                it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
    }

    /** Entfernt den Favoriten; war die Wallbox keiner, passiert nichts. */
    suspend fun remove(userId: Uuid, wallboxId: Uuid) {
        db.tx { Favorites.deleteWhere { (Favorites.userId eq userId) and (Favorites.wallboxId eq wallboxId) } }
    }
}
