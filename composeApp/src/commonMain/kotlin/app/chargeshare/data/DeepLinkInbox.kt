package app.chargeshare.data

import app.chargeshare.shared.model.AppDeepLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ziele, auf die ein Deep Link (`chargeshare://…`) die App führen kann. */
sealed interface DeepLink {
    /** Rückkehr vom Stripe-Onboarding: Kontostatus sofort prüfen. */
    data object OnboardingDone : DeepLink

    /** Der Onboarding-Link ist abgelaufen: neuen Link holen. */
    data object OnboardingRefresh : DeepLink

    /** Link aus der „Passwort vergessen“-Mail; [token] ist das Einmal-Token für `POST /auth/reset-password`. */
    data class ResetPassword(val token: String) : DeepLink

    companion object {
        /** Erkennt [uri]; unbekannte oder fremde Adressen ergeben `null`. */
        fun parse(uri: String?): DeepLink? {
            val trimmed = uri?.trim() ?: return null
            val path = trimmed.substringBefore('?').trimEnd('/')
            return when (path) {
                AppDeepLinks.ONBOARDING_DONE -> OnboardingDone
                AppDeepLinks.ONBOARDING_REFRESH -> OnboardingRefresh
                AppDeepLinks.RESET_PASSWORD -> tokenOf(trimmed)?.let(::ResetPassword)
                else -> null
            }
        }

        private fun tokenOf(uri: String): String? =
            uri.substringAfter('?', "").split('&')
                .firstOrNull { it.startsWith("token=") }
                ?.removePrefix("token=")
                ?.takeIf { it.isNotBlank() }
    }
}

/**
 * Briefkasten für Deep Links zwischen Plattform (Android-Intent, iOS-URL) und UI.
 * Ein Link bleibt liegen, bis die UI ihn mit [consume] abholt – so geht auch ein Kaltstart über den Link nicht verloren.
 */
class DeepLinkInbox {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    /** Legt [uri] ab, falls sie ein bekannter Deep Link ist. @return `true`, wenn sie erkannt wurde */
    fun offer(uri: String?): Boolean {
        val link = DeepLink.parse(uri) ?: return false
        _pending.value = link
        return true
    }

    /** Quittiert [link]; ein inzwischen eingetroffener neuer Link bleibt erhalten. */
    fun consume(link: DeepLink) {
        if (_pending.value == link) _pending.value = null
    }
}
