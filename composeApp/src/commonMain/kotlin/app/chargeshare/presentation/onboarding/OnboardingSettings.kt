package app.chargeshare.presentation.onboarding

import app.chargeshare.data.PreferenceStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Seiten der Einführung in der Reihenfolge, in der sie erscheinen. Texte und Grafiken wählt die UI je Seite. */
enum class OnboardingPage { Find, Book, Charge, Host }

/**
 * Steuert die Einführung beim ersten Start: ob sie gerade gezeigt wird und ob der Nutzer sie schon gesehen hat.
 *
 * Das „gesehen“-Flag liegt lokal im [PreferenceStorage] (also pro Gerät, nicht pro Konto). Wer die Einführung überspringt
 * oder beendet, bekommt sie nicht wieder automatisch. Über [show] lässt sie sich jederzeit erneut aufrufen (Profil),
 * ohne das Flag zu ändern.
 */
class OnboardingSettings(private val storage: PreferenceStorage) {
    private val _visible = MutableStateFlow(storage.read(KEY_SEEN) != SEEN)

    /** `true`, solange die Einführung angezeigt werden soll. */
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /** Zeigt die Einführung erneut (z. B. aus dem Profil). */
    fun show() {
        _visible.value = true
    }

    /** Schließt die Einführung (nach der letzten Seite oder „Überspringen“) und merkt sich, dass sie gesehen wurde. */
    fun finish() {
        storage.write(KEY_SEEN, SEEN)
        _visible.value = false
    }

    private companion object {
        const val KEY_SEEN = "onboarding_seen"
        const val SEEN = "1"
    }
}
