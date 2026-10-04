package app.chargeshare.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * Öffnet Adressen im Safari (`UIApplication.openURL`).
 *
 * Ungetestet auf echter Hardware/Simulator: auf Linux ist nur die Kompilierung möglich.
 */
class IosUrlOpener : UrlOpener {
    override fun open(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
        return true
    }
}
