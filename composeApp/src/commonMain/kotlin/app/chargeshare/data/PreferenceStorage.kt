package app.chargeshare.data

/**
 * Einfacher, dauerhafter Schlüssel-Wert-Speicher für **nicht geheime** Einstellungen (z. B. Dunkelmodus).
 * Geheimnisse gehören in den `TokenStore`. Plattform-Implementierungen: siehe `createPreferenceStorage`.
 */
interface PreferenceStorage {
    fun read(key: String): String?

    /** `null` löscht den Eintrag. */
    fun write(key: String, value: String?)
}

/** Speicher nur im Arbeitsspeicher (für Tests und Vorschauen). */
class InMemoryPreferenceStorage(initial: Map<String, String> = emptyMap()) : PreferenceStorage {
    private val values = initial.toMutableMap()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
