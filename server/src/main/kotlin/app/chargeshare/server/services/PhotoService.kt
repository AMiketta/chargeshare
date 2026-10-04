package app.chargeshare.server.services

import app.chargeshare.server.storage.PhotoNames
import app.chargeshare.server.storage.PhotoProcessor
import app.chargeshare.server.storage.PhotoStorage
import app.chargeshare.server.util.notFound
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.Wallbox
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fotos einer Wallbox: hochladen, ausliefern, entfernen.
 *
 * In `wallboxes.photos` steht der **relative** Pfad (`/api/v1/wallboxes/{id}/photos/{name}`); die Apps setzen ihn auf die
 * Server-Adresse. So bleibt die Datenbank beim Umzug auf eine andere Domain oder einen Objektspeicher unverändert.
 */
class PhotoService(
    private val wallboxes: WallboxService,
    private val storage: PhotoStorage,
    private val maxUploadBytes: Long,
) {
    /**
     * Prüft Berechtigung und Bild, speichert das normalisierte JPEG und trägt es in die Fotoliste ein.
     * Erst die Datei, dann der Datenbankeintrag; scheitert dieser (z. B. Obergrenze), wird die Datei wieder entfernt.
     */
    suspend fun upload(wallboxId: Uuid, userId: Uuid, bytes: ByteArray): Wallbox {
        wallboxes.assertCanEdit(wallboxId, userId)
        if (bytes.isEmpty()) throw validationError("photo: file is empty")
        if (bytes.size > maxUploadBytes) throw validationError("photo: file is larger than ${maxUploadBytes / BYTES_PER_MB} MB")
        val processed = withContext(Dispatchers.Default) { PhotoProcessor.normalize(bytes) }
        val name = PhotoNames.generate()
        withContext(Dispatchers.IO) {
            storage.save(wallboxId, name, processed.full)
            storage.save(wallboxId, PhotoNames.thumbnailOf(name), processed.thumbnail)
        }
        try {
            return wallboxes.addPhoto(wallboxId, userId, path(wallboxId, name))
        } catch (e: Exception) {
            deleteFiles(wallboxId, name)
            throw e
        }
    }

    suspend fun remove(wallboxId: Uuid, userId: Uuid, name: String): Wallbox {
        if (!PhotoNames.isValid(name) || name.endsWith("-s.jpg")) throw notFound("photo not found")
        val updated = wallboxes.removePhoto(wallboxId, userId, path(wallboxId, name))
        deleteFiles(wallboxId, name)
        return updated
    }

    private suspend fun deleteFiles(wallboxId: Uuid, name: String) = withContext(Dispatchers.IO) {
        storage.delete(wallboxId, name)
        storage.delete(wallboxId, PhotoNames.thumbnailOf(name))
    }

    /** Öffentlich lesbar (wie die Wallbox selbst). Namen, die nicht dem Schema entsprechen, erreichen den Speicher nie. */
    suspend fun read(wallboxId: Uuid, name: String): ByteArray {
        if (!PhotoNames.isValid(name)) throw notFound("photo not found")
        return withContext(Dispatchers.IO) { storage.read(wallboxId, name) } ?: throw notFound("photo not found")
    }

    /** Räumt die Dateien einer gelöschten Wallbox auf (best effort: ein Fehler macht das Löschen nicht rückgängig). */
    suspend fun purge(wallboxId: Uuid) {
        runCatching { withContext(Dispatchers.IO) { storage.deleteAll(wallboxId) } }
    }

    companion object {
        private const val BYTES_PER_MB = 1_000_000L

        fun path(wallboxId: Uuid, name: String): String = "/api/v1/wallboxes/$wallboxId/photos/$name"
    }
}
