package app.chargeshare.platform

/** Gibt eine heruntergeladene Quittung (PDF) an den Nutzer weiter: teilen, speichern oder öffnen, je nach Plattform. */
interface ReceiptSharer {
    /** `false`, wenn diese Plattform Quittungen nicht ausgeben kann; die UI blendet den Knopf dann aus. */
    val isSupported: Boolean

    /** @return `false`, wenn die Datei nicht ausgegeben werden konnte (kein passendes Programm, Schreibfehler) */
    fun share(fileName: String, pdf: ByteArray): Boolean
}

/** Rückfall für Plattformen ohne Dateiausgabe (iOS ist noch nicht angebunden). */
object UnsupportedReceiptSharer : ReceiptSharer {
    override val isSupported: Boolean = false

    override fun share(fileName: String, pdf: ByteArray): Boolean = false
}
