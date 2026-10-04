package app.chargeshare.server.routes

import app.chargeshare.server.payments.ONBOARDING_REFRESH_PATH
import app.chargeshare.server.payments.ONBOARDING_RETURN_PATH
import app.chargeshare.server.util.badRequest
import app.chargeshare.shared.model.AppDeepLinks
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Öffentliche Zwischenseiten, die den Browser per Deep Link in die App schicken: Rückkehr vom Stripe-Onboarding
 * (`chargeshare://onboarding-done`) und der Link aus der „Passwort vergessen“-Mail (`chargeshare://reset-password?token=…`).
 * Stripe und E-Mail-Programme kennen keine eigenen URL-Schemata, daher der Umweg über https. Als Rückfall gibt es einen Button,
 * falls der Browser die automatische Weiterleitung blockiert. Die Seiten brauchen keinen Login und enthalten keine Nutzerdaten.
 */
fun Route.appLinkPageRoutes() {
    get(ONBOARDING_RETURN_PATH) {
        call.respondText(
            returnPage(AppDeepLinks.ONBOARDING_DONE, "Zurück zu ChargeShare", "Die App prüft jetzt den Stand deiner Auszahlungen."),
            ContentType.Text.Html,
        )
    }
    get(RESET_PASSWORD_PATH) {
        // Das Token landet in HTML und JavaScript: nur die Zeichen der Base64-URL-Kodierung sind erlaubt (kein Einschleusen von Markup).
        val token = call.request.queryParameters["token"].orEmpty()
        if (!TOKEN_PATTERN.matches(token)) throw badRequest("invalid token")
        val link = "${AppDeepLinks.RESET_PASSWORD}?token=$token"
        call.respondText(
            returnPage(link, "Passwort in ChargeShare ändern", "Die App öffnet sich, damit du ein neues Passwort vergeben kannst."),
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

private const val RESET_PASSWORD_PATH = "/reset-password"
private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{16,128}$")

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
