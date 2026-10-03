package app.chargeshare.shared.api

import kotlinx.serialization.json.Json

/**
 * Gemeinsame JSON-Konfiguration fuer Client und Server.
 * - `explicitNulls = false`: `null` wird nicht geschrieben (entspricht Go `omitempty`)
 * - `coerceInputValues`: `null` (Go serialisiert nil-Slices/Maps als `null`) -> Default-Wert
 */
val ChargeShareJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}
