package app.chargeshare.platform

import java.awt.Desktop
import java.net.URI

/** Öffnet Adressen im Standard-Browser des Desktops (`java.awt.Desktop`), falls die Plattform das unterstützt. */
class DesktopUrlOpener : UrlOpener {
    override fun open(url: String): Boolean {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) return false
        return runCatching { Desktop.getDesktop().browse(URI(url)) }.isSuccess
    }
}
