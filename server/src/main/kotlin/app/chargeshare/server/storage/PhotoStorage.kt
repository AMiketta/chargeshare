package app.chargeshare.server.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import kotlin.uuid.Uuid

/**
 * Ablage für Wallbox-Fotos. Eine Schnittstelle, damit später ein Objektspeicher (S3 o. Ä.) statt des Dateisystems
 * eingesetzt werden kann, ohne Dienste oder Routen anzufassen.
 *
 * Dateinamen kommen immer vom Server ([PhotoNames.generate]); Namen aus Anfragen werden mit [PhotoNames.isValid] geprüft.
 */
interface PhotoStorage {
    fun save(wallboxId: Uuid, name: String, bytes: ByteArray)

    /** @return die Bytes oder `null`, wenn es das Foto nicht gibt */
    fun read(wallboxId: Uuid, name: String): ByteArray?

    fun delete(wallboxId: Uuid, name: String)

    /** Entfernt alle Fotos einer Wallbox (beim Löschen der Wallbox). */
    fun deleteAll(wallboxId: Uuid)
}

/** Namensschema der gespeicherten Fotos: `<uuid>.jpg` und die kleine Fassung `<uuid>-s.jpg` (der Server speichert immer JPEG). */
object PhotoNames {
    private val PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(-s)?\\.jpg$")

    fun generate(): String = "${Uuid.random()}.jpg"

    /** Name der kleinen Fassung (`<uuid>-s.jpg`) zur großen. */
    fun thumbnailOf(name: String): String = name.removeSuffix(".jpg") + "-s.jpg"

    /** Schützt vor Pfad-Tricks (`../`): nur genau dieses Muster erreicht das Dateisystem. */
    fun isValid(name: String): Boolean = PATTERN.matches(name)
}

/** Speichert unter `<root>/<wallboxId>/<name>`. Schreiben ist atomar (temporäre Datei, dann umbenennen). */
class FileSystemPhotoStorage(private val root: Path) : PhotoStorage {

    override fun save(wallboxId: Uuid, name: String, bytes: ByteArray) {
        val target = resolve(wallboxId, name)
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, "upload-", ".tmp")
        try {
            Files.write(temp, bytes)
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override fun read(wallboxId: Uuid, name: String): ByteArray? =
        resolve(wallboxId, name).takeIf { Files.isRegularFile(it) }?.let { Files.readAllBytes(it) }

    override fun delete(wallboxId: Uuid, name: String) {
        Files.deleteIfExists(resolve(wallboxId, name))
    }

    override fun deleteAll(wallboxId: Uuid) {
        val directory = root.resolve(wallboxId.toString())
        if (!Files.isDirectory(directory)) return
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    private fun resolve(wallboxId: Uuid, name: String): Path {
        require(PhotoNames.isValid(name)) { "invalid photo name" }
        return root.resolve(wallboxId.toString()).resolve(name)
    }
}

/** Nur im Speicher; für Tests und Läufe ohne beschreibbares Dateisystem. */
class InMemoryPhotoStorage : PhotoStorage {
    private val files = java.util.concurrent.ConcurrentHashMap<Pair<Uuid, String>, ByteArray>()

    val size: Int get() = files.size

    override fun save(wallboxId: Uuid, name: String, bytes: ByteArray) {
        files[wallboxId to name] = bytes
    }

    override fun read(wallboxId: Uuid, name: String): ByteArray? = files[wallboxId to name]

    override fun delete(wallboxId: Uuid, name: String) {
        files.remove(wallboxId to name)
    }

    override fun deleteAll(wallboxId: Uuid) {
        files.keys.removeIf { it.first == wallboxId }
    }
}
