package app.chargeshare.platform

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsModule

/**
 * Zeitzonen-Datenbank für kotlinx-datetime im Browser (npm `@js-joda/timezone`).
 *
 * Ohne sie liefert `TimeZone.currentSystemDefault()` auf Wasm/JS nur die Zone `"SYSTEM"`: der Server lehnt
 * `tz=SYSTEM` ab (Host-Bereich: HTTP 400) und Zonen-Rechnungen schlagen fehl. Das Modul muss geladen sein,
 * bevor die erste Zeitzone abgefragt wird, deshalb ruft `web/Main.kt` [loadTimeZoneDatabase] als Erstes auf.
 */
@OptIn(ExperimentalWasmJsInterop::class)
@JsModule("@js-joda/timezone")
private external object JsJodaTimeZoneModule

private var timeZoneModule: Any? = null

fun loadTimeZoneDatabase() {
    timeZoneModule = JsJodaTimeZoneModule
}
