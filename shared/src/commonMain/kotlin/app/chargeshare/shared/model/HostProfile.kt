package app.chargeshare.shared.model

import app.chargeshare.shared.util.InstantAsStringSerializer
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Schwellen, ab denen ein Host als „Superhost“ gilt; der Server wertet sie aus, die Apps zeigen nur [HostProfile.isSuperhost]. */
object SuperhostCriteria {
    /** Mindestanzahl abgeschlossener Ladevorgänge auf den eigenen Wallboxen. */
    const val MIN_SESSIONS = 10

    /** Mindestanzahl Bewertungen, damit der Durchschnitt aussagekräftig ist. */
    const val MIN_RATING_COUNT = 5

    /** Mindest-Durchschnitt der Bewertungen (Sterne). */
    const val MIN_RATING_AVG = 4.5
}

/**
 * Öffentliches Profil des Hosts einer Wallbox (`GET /wallboxes/{id}/host`) für die Host-Karte der Detailseite.
 * Enthält bewusst keine Kontaktdaten; der Name ist auf „Vorname N.“ gekürzt.
 *
 * @property name gekürzter Anzeigename, z. B. „Andreas M.“
 * @property memberSince Registrierung des Hosts
 * @property sessions abgeschlossene Ladevorgänge auf allen Wallboxen des Hosts
 * @property ratingAvg Durchschnitt aller Bewertungen auf den Wallboxen des Hosts; `null`, solange es keine gibt
 * @property ratingCount Anzahl dieser Bewertungen
 * @property isSuperhost Server-seitig aus [SuperhostCriteria] abgeleitet
 */
@Serializable
data class HostProfile(
    val name: String,
    @SerialName("member_since") @Serializable(InstantAsStringSerializer::class) val memberSince: Instant,
    val sessions: Int = 0,
    @SerialName("rating_avg") val ratingAvg: Double? = null,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("is_superhost") val isSuperhost: Boolean = false,
)

/** Antwort von `GET /users/me/favorites`: die Wallbox-IDs, neueste Favoriten zuerst. */
@Serializable
data class FavoritesResponse(
    @SerialName("wallbox_ids") val wallboxIds: List<String> = emptyList(),
)
