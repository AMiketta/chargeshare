package app.chargeshare.platform

/**
 * Ein ausgewähltes Foto, schon für den Upload vorbereitet.
 *
 * @property bytes Bilddaten (JPEG oder PNG; Android liefert ein auf 1600 px verkleinertes, richtig gedrehtes JPEG)
 */
class PickedPhoto(val bytes: ByteArray, val fileName: String, val mimeType: String)

/** Ausgang der Fotoauswahl. */
sealed interface PhotoPickResult {
    class Picked(val photo: PickedPhoto) : PhotoPickResult

    /** Der Nutzer hat die Auswahl abgebrochen. */
    data object Canceled : PhotoPickResult

    /** Die Datei konnte nicht gelesen werden oder ist zu groß. */
    data object Unreadable : PhotoPickResult
}

/** Fotoauswahl der Plattform (Android: Photo Picker, Desktop: Dateidialog). */
interface PhotoPicker {
    /** `false`, wenn diese Plattform keine Auswahl anbietet; die UI blendet „Foto hinzufügen“ dann aus. */
    val isSupported: Boolean

    suspend fun pick(): PhotoPickResult
}

/** Rückfall für Plattformen ohne Fotoauswahl (Web und iOS sind noch nicht angebunden). */
object UnsupportedPhotoPicker : PhotoPicker {
    override val isSupported: Boolean = false

    override suspend fun pick(): PhotoPickResult = PhotoPickResult.Canceled
}
