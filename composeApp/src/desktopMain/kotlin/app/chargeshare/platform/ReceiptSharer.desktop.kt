package app.chargeshare.platform

import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Schreibt die Quittung in ein temporäres Verzeichnis und öffnet sie im PDF-Programm des Desktops. */
class DesktopReceiptSharer : ReceiptSharer {
    override val isSupported: Boolean
        get() = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)

    override fun share(fileName: String, pdf: ByteArray): Boolean {
        if (!isSupported) return false
        return try {
            val directory = Files.createTempDirectory("chargeshare-receipt").toFile()
            val file = File(directory, fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            file.writeBytes(pdf)
            file.deleteOnExit()
            directory.deleteOnExit()
            Desktop.getDesktop().open(file)
            true
        } catch (e: IOException) {
            false
        }
    }
}
