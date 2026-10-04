package app.chargeshare.platform

import kotlin.io.encoding.Base64

/** Startet im Browser den Download der Quittung (Data-URL mit Download-Attribut). */
class BrowserReceiptSharer : ReceiptSharer {
    override val isSupported: Boolean = true

    override fun share(fileName: String, pdf: ByteArray): Boolean {
        downloadDataUrl(fileName, "data:application/pdf;base64," + Base64.encode(pdf))
        return true
    }
}

private fun downloadDataUrl(fileName: String, dataUrl: String): Unit = js(
    "{ const a = document.createElement('a'); a.href = dataUrl; a.download = fileName; document.body.appendChild(a); a.click(); a.remove(); }",
)
