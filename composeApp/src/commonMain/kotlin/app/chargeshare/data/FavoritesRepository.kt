package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi

/** Favoriten (Herz) des angemeldeten Nutzers. */
interface FavoritesRepository {
    /** IDs der favorisierten Wallboxen. */
    suspend fun ids(): Outcome<Set<String>>

    /** Merkt die Wallbox; mehrfaches Merken ist unschädlich. */
    suspend fun add(wallboxId: String): Outcome<Unit>

    /** Entfernt die Wallbox aus den Favoriten. */
    suspend fun remove(wallboxId: String): Outcome<Unit>
}

class ApiFavoritesRepository(private val api: ChargeShareApi) : FavoritesRepository {
    override suspend fun ids(): Outcome<Set<String>> = runCatchingOutcome { api.favoriteIds().toSet() }

    override suspend fun add(wallboxId: String): Outcome<Unit> = runCatchingOutcome { api.addFavorite(wallboxId) }

    override suspend fun remove(wallboxId: String): Outcome<Unit> = runCatchingOutcome { api.removeFavorite(wallboxId) }
}
