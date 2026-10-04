package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.model.ChargingSession
import app.chargeshare.shared.model.CreateWallboxRequest
import app.chargeshare.shared.model.GeocodeRequest
import app.chargeshare.shared.model.GeocodeResponse
import app.chargeshare.shared.model.HostEarnings
import app.chargeshare.shared.model.UpdateWallboxRequest
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.shared.model.WallboxSearch
import kotlinx.datetime.TimeZone

/** Host-Funktionen: Einnahmen, Sessions, eigene Wallboxen. */
interface HostRepository {
    /** Einnahmen; Tage, Monate und Woche richten sich nach [zone] (Zeitzone des Geräts). */
    suspend fun earnings(zone: TimeZone): Outcome<HostEarnings>
    suspend fun sessions(): Outcome<List<ChargingSession>>

    /**
     * Eigene Wallboxen des Nutzers [ownerId].
     *
     * Das Backend hat (noch) keinen Endpunkt „meine Wallboxen“; daher werden die Suchergebnisse
     * (max. [MAX_WALLBOXES]) nach `owner_id` gefiltert.
     */
    suspend fun myWallboxes(ownerId: String): Outcome<List<Wallbox>>
    suspend fun createWallbox(request: CreateWallboxRequest): Outcome<Wallbox>

    /** Ändert die Wallbox; der Server erlaubt das nur dem Eigentümer (sonst [AppError.Forbidden]). */
    suspend fun updateWallbox(wallboxId: String, request: UpdateWallboxRequest): Outcome<Wallbox>
    suspend fun setOnline(wallboxId: String, isOnline: Boolean): Outcome<Boolean>

    /** Lädt ein Foto hoch; die Antwort ist die aktualisierte Wallbox mit allen Fotos. */
    suspend fun uploadPhoto(wallboxId: String, bytes: ByteArray, fileName: String, mimeType: String): Outcome<Wallbox>

    /** Entfernt ein Foto ([photoPath] = Eintrag aus [Wallbox.photos]). */
    suspend fun removePhoto(wallboxId: String, photoPath: String): Outcome<Wallbox>

    /** Koordinaten zur Adresse. [AppError.NotFound] = unbekannte Adresse, [AppError.Server] = Adresssuche nicht verfügbar. */
    suspend fun geocode(address: String, postalCode: String, city: String): Outcome<GeocodeResponse>

    companion object {
        const val MAX_WALLBOXES = 100
    }
}

class ApiHostRepository(private val api: ChargeShareApi) : HostRepository {

    override suspend fun earnings(zone: TimeZone): Outcome<HostEarnings> =
        runCatchingOutcome { api.hostEarnings(timeZone = zone.id) }

    override suspend fun sessions(): Outcome<List<ChargingSession>> =
        runCatchingOutcome { api.hostSessions(limit = SESSION_LIMIT).sessions }

    override suspend fun myWallboxes(ownerId: String): Outcome<List<Wallbox>> =
        runCatchingOutcome {
            api.searchWallboxes(WallboxSearch(limit = HostRepository.MAX_WALLBOXES)).wallboxes
                .filter { it.ownerId == ownerId }
        }

    override suspend fun createWallbox(request: CreateWallboxRequest): Outcome<Wallbox> =
        runCatchingOutcome { api.createWallbox(request) }

    override suspend fun updateWallbox(wallboxId: String, request: UpdateWallboxRequest): Outcome<Wallbox> =
        runCatchingOutcome { api.updateWallbox(wallboxId, request) }

    override suspend fun setOnline(wallboxId: String, isOnline: Boolean): Outcome<Boolean> =
        runCatchingOutcome { api.setWallboxOnline(wallboxId, isOnline).isOnline }

    override suspend fun uploadPhoto(wallboxId: String, bytes: ByteArray, fileName: String, mimeType: String): Outcome<Wallbox> =
        runCatchingOutcome { api.uploadWallboxPhoto(wallboxId, bytes, fileName, mimeType) }

    override suspend fun removePhoto(wallboxId: String, photoPath: String): Outcome<Wallbox> =
        runCatchingOutcome { api.deleteWallboxPhoto(wallboxId, photoPath) }

    override suspend fun geocode(address: String, postalCode: String, city: String): Outcome<GeocodeResponse> =
        runCatchingOutcome { api.geocode(GeocodeRequest(address, postalCode, city)) }

    private companion object {
        const val SESSION_LIMIT = 20
    }
}
