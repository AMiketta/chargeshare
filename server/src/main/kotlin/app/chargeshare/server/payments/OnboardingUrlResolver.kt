package app.chargeshare.server.payments

import app.chargeshare.server.config.AppConfig

/** Pfade der öffentlichen Rückkehr-Seiten (siehe `routes/OnboardingPages.kt`). */
const val ONBOARDING_RETURN_PATH = "/onboarding/return"
const val ONBOARDING_REFRESH_PATH = "/onboarding/refresh"

/**
 * Ermittelt die Adressen, auf die Stripe nach dem Onboarding zurückleitet.
 *
 * Stripe kennt keine eigenen URL-Schemata (`chargeshare://…`), daher zeigen die Adressen auf zwei Seiten dieses Servers,
 * die den Browser per Deep Link zurück in die App schicken. Vorrang: ausdrückliche URL aus der Konfiguration, dann
 * `PUBLIC_BASE_URL`, zuletzt die Adresse, unter der die App den Server gerade erreicht ([requestBaseUrl]; im Emulator
 * `http://10.0.2.2:8080`, im Browser des Emulators ebenfalls erreichbar).
 */
fun resolveOnboardingUrls(config: AppConfig, requestBaseUrl: String): OnboardingUrls {
    val base = (config.publicBaseUrl ?: requestBaseUrl).trimEnd('/')
    return OnboardingUrls(
        returnUrl = config.onboardingReturnUrl ?: (base + ONBOARDING_RETURN_PATH),
        refreshUrl = config.onboardingRefreshUrl ?: (base + ONBOARDING_REFRESH_PATH),
    )
}
