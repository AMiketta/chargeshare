package app.chargeshare.platform

import kotlinx.browser.window

/** Öffnet Adressen in einem neuen Browser-Tab. */
class BrowserUrlOpener : UrlOpener {
    override fun open(url: String): Boolean {
        window.open(url, "_blank")
        return true
    }
}
