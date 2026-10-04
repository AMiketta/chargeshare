package app.chargeshare.server.mail

import app.chargeshare.server.config.AppConfig
import org.slf4j.LoggerFactory

/** Eine einfache Text-E-Mail. */
data class MailMessage(val to: String, val subject: String, val body: String)

/**
 * Versand von E-Mails, austauschbar: Entwicklung und Tests nutzen den [LoggingMailer]; für den Livebetrieb kommt eine
 * Implementierung gegen SMTP oder einen Mail-Dienst hinzu (siehe README „Passwort vergessen“).
 */
interface Mailer {
    /** Verschickt [message]. Fehler werden geworfen; der Aufrufer entscheidet, ob sie dem Nutzer sichtbar werden. */
    fun send(message: MailMessage)

    companion object {
        fun from(config: AppConfig): Mailer = LoggingMailer(logBody = !config.isProduction)
    }
}

/**
 * Entwicklungs-Mailer: schreibt die Mail ins Log, statt sie zu versenden. Der Text enthält den Reset-Link und gehört
 * deshalb **nie** in ein Produktionslog – mit [logBody] = `false` (Produktion) wird nur der Empfänger-Hinweis protokolliert,
 * die Mail geht verloren, bis ein echter Mailer konfiguriert ist.
 */
class LoggingMailer(private val logBody: Boolean) : Mailer {
    private val log = LoggerFactory.getLogger(LoggingMailer::class.java)

    override fun send(message: MailMessage) {
        if (logBody) {
            log.info("[dev-mail] to={} subject={}\n{}", message.to, message.subject, message.body)
        } else {
            log.warn("no mailer configured: mail '{}' was not sent", message.subject)
        }
    }
}
