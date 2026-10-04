package app.chargeshare.platform

import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fotoauswahl am Desktop über den Swing-Dateidialog. Die Datei wird unverändert hochgeladen; der Server verkleinert sie.
 * Dateien über [MAX_BYTES] gelten als nicht lesbar, weil der Server sie ohnehin ablehnen würde.
 */
class DesktopPhotoPicker : PhotoPicker {
    override val isSupported: Boolean = true

    override suspend fun pick(): PhotoPickResult {
        val file = chooseFile() ?: return PhotoPickResult.Canceled
        return withContext(Dispatchers.IO) { read(file) }
    }

    private suspend fun chooseFile(): File? = withContext(Dispatchers.Main.immediate) {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("Fotos (JPEG, PNG)", "jpg", "jpeg", "png")
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    private fun read(file: File): PhotoPickResult {
        val mimeType = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> return PhotoPickResult.Unreadable
        }
        return try {
            if (file.length() > MAX_BYTES) {
                PhotoPickResult.Unreadable
            } else {
                PhotoPickResult.Picked(PickedPhoto(file.readBytes(), file.name, mimeType))
            }
        } catch (_: IOException) {
            PhotoPickResult.Unreadable
        }
    }

    private companion object {
        const val MAX_BYTES = 10_000_000L
    }
}
