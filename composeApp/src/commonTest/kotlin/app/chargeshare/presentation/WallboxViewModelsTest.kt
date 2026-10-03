package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.Outcome
import app.chargeshare.presentation.wallbox.DEFAULT_CENTER
import app.chargeshare.presentation.wallbox.WallboxDetailViewModel
import app.chargeshare.presentation.wallbox.WallboxListViewModel
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.testing.FakeWallboxRepository
import app.chargeshare.testing.FixedClock
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.testWallbox
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class WallboxViewModelsTest {
    private val repository = FakeWallboxRepository()

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    @Test
    fun listSearchesAroundDefaultCenterOnStart() {
        repository.searchResult = Outcome.Success(listOf(testWallbox("a"), testWallbox("b")))
        val state = WallboxListViewModel(repository).state.value
        assertEquals(DEFAULT_CENTER, repository.searches.single().first)
        assertEquals(2, state.wallboxes.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun listShowsErrorWhenSearchFails() {
        repository.searchResult = failure(AppError.Network)
        val state = WallboxListViewModel(repository).state.value
        assertEquals(AppError.Network, state.error)
        assertEquals(emptyList(), state.wallboxes)
    }

    @Test
    fun changingFilterTriggersNewSearchWithThatFilter() {
        val viewModel = WallboxListViewModel(repository)
        viewModel.onConnectorChange(ConnectorType.CCS)
        viewModel.onMinPowerChange(11.0)
        viewModel.onRadiusChange(3_000.0)
        val filter = repository.searches.last().second
        assertEquals(ConnectorType.CCS, filter.connector)
        assertEquals(11.0, filter.minPowerKw)
        assertEquals(3_000.0, filter.radiusM)
        assertEquals(4, repository.searches.size)
    }

    @Test
    fun queryFiltersLocallyByNameAddressOrCity() {
        repository.searchResult = Outcome.Success(
            listOf(testWallbox("a", name = "Weinbergweg 5"), testWallbox("b", name = "Am Deich 22")),
        )
        val viewModel = WallboxListViewModel(repository)
        viewModel.onQueryChange("deich")
        assertEquals(listOf("b"), viewModel.state.value.visibleWallboxes.map { it.id })
        assertEquals(1, repository.searches.size, "Freitext darf keine neue Server-Suche auslösen")
    }

    @Test
    fun onlyOnlineHidesOfflineWallboxes() {
        repository.searchResult = Outcome.Success(listOf(testWallbox("on"), testWallbox("off", isOnline = false)))
        val viewModel = WallboxListViewModel(repository)
        viewModel.onOnlyOnlineChange(true)
        assertEquals(listOf("on"), viewModel.state.value.visibleWallboxes.map { it.id })
    }

    @Test
    fun detailLoadsWallboxReviewsAndTodaysFreeSlots() {
        val today = LocalDate(2026, 10, 5)
        repository.slotsByDay[today] = Outcome.Success(
            listOf(Instant.parse("2026-10-05T10:00:00Z"), Instant.parse("2026-10-05T10:30:00Z")),
        )
        val viewModel = WallboxDetailViewModel("wb-1", repository, FixedClock(), TimeZone.UTC)
        val state = viewModel.state.value
        assertNotNull(state.wallbox)
        assertEquals(2, state.freeSlotsToday)
        assertFalse(state.isLoading)
    }

    @Test
    fun detailShowsErrorWhenWallboxCannotBeLoaded() {
        repository.wallboxResult = failure(AppError.NotFound)
        val state = WallboxDetailViewModel("x", repository, FixedClock(), TimeZone.UTC).state.value
        assertEquals(AppError.NotFound, state.error)
        assertNull(state.wallbox)
    }

    @Test
    fun detailStaysUsableWhenSlotsFail() {
        repository.slotsByDay[LocalDate(2026, 10, 5)] = failure(AppError.Server)
        val state = WallboxDetailViewModel("wb-1", repository, FixedClock(), TimeZone.UTC).state.value
        assertNotNull(state.wallbox)
        assertNull(state.freeSlotsToday)
        assertNull(state.error)
    }
}
