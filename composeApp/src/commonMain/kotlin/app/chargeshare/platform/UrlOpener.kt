package app.chargeshare.platform

/** Öffnet eine Web-Adresse im Browser des Geräts (z. B. den Stripe-Onboarding-Link). */
fun interface UrlOpener {
    /** @return `false`, wenn die Plattform die Adresse nicht öffnen konnte */
    fun open(url: String): Boolean
}

/** Rückfall für Plattformen ohne Browser-Anbindung. */
object UnsupportedUrlOpener : UrlOpener {
    override fun open(url: String): Boolean = false
}
