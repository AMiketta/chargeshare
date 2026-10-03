package app.chargeshare.platform

import app.chargeshare.data.PersistedTokenStore
import app.chargeshare.data.TokenStorage
import app.chargeshare.shared.api.TokenStore
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

actual fun createSecureTokenStore(context: PlatformContext): TokenStore =
    PersistedTokenStore(FileTokenStorage(File(System.getProperty("user.home"), ".chargeshare/session")))

/**
 * Speichert den Token-String in einer Datei, die nur der Besitzer lesen darf (POSIX: `rw-------`).
 * Die Datei ist **nicht verschlüsselt** – ein Desktop-Keystore ist noch nicht angebunden.
 */
private class FileTokenStorage(private val file: File) : TokenStorage {

    override fun read(): String? = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()

    override fun write(value: String?) {
        if (value == null) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.writeText(value)
        // Auf Nicht-POSIX-Dateisystemen (Windows) ist das nicht verfügbar; dann gelten die Benutzerordner-Rechte.
        runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
    }
}
