package app.chargeshare.server.routes

import app.chargeshare.server.payments.ONBOARDING_REFRESH_PATH
import app.chargeshare.server.payments.ONBOARDING_RETURN_PATH
import app.chargeshare.shared.model.AppDeepLinks
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Öffentliche Rückkehr-Seiten des Stripe-Onboardings. Stripe leitet den Browser hierher; die Seite öffnet per Deep Link
 * die App (`chargeshare://onboarding-done`) und bietet als Rückfall einen Button an, falls der Browser die automatische
 * Weiterleitung blockiert. Die Seiten enthalten keine Nutzerdaten und brauchen keinen Login.
 */
fun Route.onboardingPageRoutes() {
    get(ONBOARDING_RETURN_PATH) {
        call.respondText(
            returnPage(AppDeepLinks.ONBOARDING_DONE, "Zurück zu ChargeShare", "Die App prüft jetzt den Stand deiner Auszahlungen."),
            ContentType.Text.Html,
        )
    }
    get(ONBOARDING_REFRESH_PATH) {
        call.respondText(
            returnPage(AppDeepLinks.ONBOARDING_REFRESH, "Weiter in ChargeShare", "Der Link ist abgelaufen. Die App erstellt einen neuen."),
            ContentType.Text.Html,
        )
    }
}

private fun returnPage(deepLink: String, title: String, hint: String): String = """
    <!doctype html>
    <html lang="de">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <meta http-equiv="refresh" content="0;url=$deepLink">
      <title>ChargeShare</title>
      <style>
        body { font-family: system-ui, sans-serif; text-align: center; padding: 3rem 1.5rem; color: #1b1b18; }
        a { display: inline-block; margin-top: 1.5rem; padding: .9rem 1.6rem; border-radius: 999px;
            background: #0B6E4F; color: #fff; text-decoration: none; font-weight: 600; }
      </style>
    </head>
    <body>
      <h1>ChargeShare</h1>
      <p>$hint</p>
      <a href="$deepLink">$title</a>
      <script>window.location.replace("$deepLink");</script>
    </body>
    </html>
""".trimIndent()
