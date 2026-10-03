package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.model.ChargingSession
import app.chargeshare.shared.model.CreateWallboxRequest
import app.chargeshare.shared.model.HostEarnings
import app.chargeshare.shared.model.UpdateWallboxRequest
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.shared.model.WallboxSearch

/** Host-Funktionen: Einnahmen, Sessions, eigene Wallboxen. */
interface HostRepository {
    suspend fun earnings(): Outcome<HostEarnings>
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

    companion object {
        const val MAX_WALLBOXES = 100
    }
}

class ApiHostRepository(private val api: ChargeShareApi) : HostRepository {

    override suspend fun earnings(): Outcome<HostEarnings> = runCatchingOutcome { api.hostEarnings() }

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

    private companion object {
        const val SESSION_LIMIT = 20
    }
}
