package app.chargeshare.data

import app.chargeshare.shared.model.AppDeepLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ziele, auf die ein Deep Link (`chargeshare://…`) die App führen kann. */
enum class DeepLink {
    /** Rückkehr vom Stripe-Onboarding: Kontostatus sofort prüfen. */
    ONBOARDING_DONE,

    /** Der Onboarding-Link ist abgelaufen: neuen Link holen. */
    ONBOARDING_REFRESH,
    ;

    companion object {
        /** Erkennt [uri]; unbekannte oder fremde Adressen ergeben `null`. */
        fun parse(uri: String?): DeepLink? = when (uri?.trim()?.trimEnd('/')) {
            AppDeepLinks.ONBOARDING_DONE -> ONBOARDING_DONE
            AppDeepLinks.ONBOARDING_REFRESH -> ONBOARDING_REFRESH
            else -> null
        }
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
