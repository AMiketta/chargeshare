package app.chargeshare.data

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.ErrorCodes
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.shared.api.TokenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class DataLayerTest {

    @Test
    fun apiExceptionsAreMappedToAppErrors() = runTest {
        suspend fun errorFor(status: Int): AppError =
            (runCatchingOutcome<Unit> { throw ApiException(status, null, "x") } as Outcome.Failure).error

        assertEquals(AppError.Unauthorized, errorFor(401))
        assertEquals(AppError.Forbidden, errorFor(403))
        assertEquals(AppError.NotFound, errorFor(404))
        assertEquals(AppError.Conflict, errorFor(409))
        assertEquals(AppError.PaymentRequired, errorFor(402))
        assertEquals(AppError.RateLimited, errorFor(429))
        assertIs<AppError.Validation>(errorFor(400))
        assertEquals(AppError.Server, errorFor(503))
        assertEquals(AppError.Unknown, errorFor(418))
    }

    @Test
    fun pendingHostPayoutsHaveTheirOwnError() = runTest {
        val result = runCatchingOutcome<Unit> { throw ApiException(409, ErrorCodes.HOST_PAYOUTS_PENDING, "Der Host richtet gerade seine Auszahlungen ein.") }
        assertEquals(Outcome.Failure(AppError.HostPayoutsPending), result)
        // Ein normaler Konflikt (Slot vergeben) bleibt ein Konflikt
        assertEquals(Outcome.Failure(AppError.Conflict), runCatchingOutcome<Unit> { throw ApiException(409, ErrorCodes.CONFLICT, "x") })
    }

    @Test
    fun otherExceptionsBecomeNetworkErrors() = runTest {
        val result = runCatchingOutcome<Unit> { error("Verbindung weg") }
        assertEquals(Outcome.Failure(AppError.Network), result)
    }

    @Test
    fun cancellationIsRethrownAndNeverSwallowed() = runTest {
        assertFailsWith<CancellationException> { runCatchingOutcome<Unit> { throw CancellationException("abgebrochen") } }
    }

    @Test
    fun successWrapsValue() = runTest {
        assertEquals(Outcome.Success(42), runCatchingOutcome { 42 })
    }

    @Test
    fun localDayInBerlinTouchesTwoUtcDatesInSummer() {
        val dates = utcDatesTouchedBy(LocalDate(2026, 7, 1), TimeZone.of("Europe/Berlin"))
        assertEquals(listOf(LocalDate(2026, 6, 30), LocalDate(2026, 7, 1)), dates)
    }

    @Test
    fun localUtcDayTouchesOneUtcDate() {
        assertEquals(listOf(LocalDate(2026, 7, 1)), utcDatesTouchedBy(LocalDate(2026, 7, 1), TimeZone.UTC))
    }

    @Test
    fun slotsBecomeInstantsOnTheirUtcDateAndBadTimesAreSkipped() {
        val slots = listOf(TimeSlot("09:00", "09:30"), TimeSlot("kaputt", "x"), TimeSlot("22:30", "23:00"))
        assertEquals(
            listOf(Instant.parse("2026-10-05T09:00:00Z"), Instant.parse("2026-10-05T22:30:00Z")),
            slots.toStartInstants(LocalDate(2026, 10, 5)),
        )
    }

    @Test
    fun persistedTokenStoreSavesLoadsAndClearsViaStorage() = runTest {
        val storage = MemoryStorage()
        PersistedTokenStore(storage).save("acc", "ref")
        val reloaded = PersistedTokenStore(storage)
        assertEquals("acc", reloaded.accessToken())
        assertEquals("ref", reloaded.refreshToken())
        reloaded.clear()
        assertNull(PersistedTokenStore(storage).accessToken())
        assertNull(storage.value)
    }

    @Test
    fun corruptedStoredValueIsTreatedAsLoggedOut() = runTest {
        val store: TokenStore = PersistedTokenStore(MemoryStorage("nur-ein-teil"))
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
    }

    private class MemoryStorage(var value: String? = null) : TokenStorage {
        override fun read(): String? = value
        override fun write(value: String?) {
            this.value = value
        }
    }
}
