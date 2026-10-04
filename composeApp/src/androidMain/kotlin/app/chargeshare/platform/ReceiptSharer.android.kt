package app.chargeshare.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Legt die Quittung in den App-Cache (`cache/receipts`) und öffnet den Android-Teilen-Dialog (Speichern, Drucken, Mail …).
 * Der Zugriff läuft über einen `FileProvider`, die Datei ist also nur für die gewählte App lesbar.
 */
class AndroidReceiptSharer(private val context: Context) : ReceiptSharer {
    override val isSupported: Boolean = true

    override fun share(fileName: String, pdf: ByteArray): Boolean {
        val file = try {
            val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            // Alte Quittungen nicht liegen lassen: im Cache soll höchstens die aktuelle Datei sein.
            directory.listFiles()?.forEach { it.delete() }
            File(directory, safeName(fileName)).also { it.writeBytes(pdf) }
        } catch (e: IOException) {
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val DIRECTORY = "receipts"
    }
}
