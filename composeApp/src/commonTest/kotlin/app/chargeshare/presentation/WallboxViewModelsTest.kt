package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.GeoPoint
import app.chargeshare.data.InMemoryPreferenceStorage
import app.chargeshare.data.Outcome
import app.chargeshare.platform.LocationPermissionStatus
import app.chargeshare.presentation.wallbox.DEFAULT_CENTER
import app.chargeshare.presentation.wallbox.SearchViewMode
import app.chargeshare.presentation.wallbox.WallboxDetailViewModel
import app.chargeshare.presentation.wallbox.WallboxListViewModel
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.testing.FakeLocationProvider
import app.chargeshare.testing.FakeWallboxRepository
import app.chargeshare.testing.FixedClock
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.testHostProfile
import app.chargeshare.testing.testWallbox
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    fun queryMatchesEveryWordInNameStreetPostalCodeOrCityAndClears() {
        repository.searchResult = Outcome.Success(
            listOf(
                testWallbox("a", name = "Wallbox Nr. 2", address = "Am Markt 1", city = "Bremen"),
                testWallbox("b", name = "Garage", address = "Weserterrassen 1", city = "Bremen"),
            ),
        )
        val viewModel = WallboxListViewModel(repository)
        viewModel.onQueryChange("nr. 2")
        assertEquals(listOf("a"), viewModel.state.value.visibleWallboxes.map { it.id })
        viewModel.onQueryChange("  WESER  bremen ")
        assertEquals(listOf("b"), viewModel.state.value.visibleWallboxes.map { it.id })
        viewModel.onQueryChange("bremen")
        assertEquals(2, viewModel.state.value.visibleWallboxes.size)
        viewModel.onQueryChange("Lemwerder")
        assertTrue(viewModel.state.value.noQueryMatches)
        viewModel.onClearQuery()
        assertEquals("", viewModel.state.value.query)
        assertFalse(viewModel.state.value.noQueryMatches)
        assertEquals(2, viewModel.state.value.visibleWallboxes.size)
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun queryCombinesWithOnlyOnlineFilter() {
        repository.searchResult = Outcome.Success(
            listOf(testWallbox("a", name = "Markt", isOnline = false), testWallbox("b", name = "Markt Süd")),
        )
        val viewModel = WallboxListViewModel(repository)
        viewModel.onQueryChange("markt")
        viewModel.onOnlyOnlineChange(true)
        assertEquals(listOf("b"), viewModel.state.value.visibleWallboxes.map { it.id })
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
    fun nearestVisibleWallboxIsActiveUntilAPinIsSelected() {
        repository.searchResult = Outcome.Success(listOf(testWallbox("a"), testWallbox("b"), testWallbox("c")))
        val viewModel = WallboxListViewModel(repository)
        assertEquals("a", viewModel.state.value.activeWallbox?.id)
        viewModel.onWallboxSelected("c")
        assertEquals("c", viewModel.state.value.activeWallbox?.id)
        assertEquals(1, repository.searches.size, "Auswahl darf keine neue Suche auslösen")
    }

    @Test
    fun selectionOfAHiddenWallboxFallsBackToTheFirstVisible() {
        repository.searchResult = Outcome.Success(listOf(testWallbox("on"), testWallbox("off", isOnline = false)))
        val viewModel = WallboxListViewModel(repository)
        viewModel.onWallboxSelected("off")
        viewModel.onOnlyOnlineChange(true)
        assertEquals("on", viewModel.state.value.activeWallbox?.id)
    }

    @Test
    fun noWallboxesMeansNoActiveWallbox() {
        assertNull(WallboxListViewModel(repository).state.value.activeWallbox)
    }

    @Test
    fun clearQuickFiltersResetsConnectorPowerAndOnlineButKeepsRadiusAndPrice() {
        val viewModel = WallboxListViewModel(repository)
        viewModel.onConnectorChange(ConnectorType.TYPE2)
        viewModel.onMinPowerChange(11.0)
        viewModel.onOnlyOnlineChange(true)
        viewModel.onRadiusChange(3_000.0)
        viewModel.onMaxPriceChange(0.4)
        repository.searches.clear()

        viewModel.onClearQuickFilters()

        val filter = viewModel.state.value.filter
        assertNull(filter.connector)
        assertNull(filter.minPowerKw)
        assertFalse(filter.onlyOnline)
        assertEquals(3_000.0, filter.radiusM)
        assertEquals(0.4, filter.maxPricePerKwh)
        assertEquals(1, repository.searches.size, "genau eine neue Suche")
    }

    @Test
    fun listIsOnlyViewWhenPlatformHasNoMap() {
        val state = WallboxListViewModel(repository, mapSupported = false).state.value
        assertFalse(state.mapSupported)
        assertEquals(SearchViewMode.List, state.viewMode)
        assertFalse(state.showMap)
    }

    @Test
    fun mapIsDefaultViewWhenPlatformSupportsIt() {
        val state = WallboxListViewModel(repository, mapSupported = true).state.value
        assertEquals(SearchViewMode.Map, state.viewMode)
        assertTrue(state.showMap)
    }

    @Test
    fun switchingViewModeTogglesBetweenMapAndListWithoutNewSearch() {
        val viewModel = WallboxListViewModel(repository, mapSupported = true)
        viewModel.onViewModeChange(SearchViewMode.List)
        assertFalse(viewModel.state.value.showMap)
        viewModel.onViewModeChange(SearchViewMode.Map)
        assertTrue(viewModel.state.value.showMap)
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun mapIsNeverShownWithoutPlatformSupportEvenIfRequested() {
        val viewModel = WallboxListViewModel(repository, mapSupported = false)
        viewModel.onViewModeChange(SearchViewMode.Map)
        assertFalse(viewModel.state.value.showMap)
    }


    @Test
    fun usesDeviceLocationWhenPermissionAlreadyGranted() {
        val location = FakeLocationProvider(
            status = LocationPermissionStatus.Granted,
            location = GeoPoint(lat = 52.5, lng = 13.4),
        )
        val viewModel = WallboxListViewModel(repository, locationProvider = location)
        assertEquals(GeoPoint(52.5, 13.4), viewModel.state.value.center)
        assertTrue(viewModel.state.value.usesDeviceLocation)
        assertFalse(viewModel.state.value.showLocationPrompt)
        assertFalse(viewModel.state.value.showLocationFallbackHint)
        assertEquals(GeoPoint(52.5, 13.4), repository.searches.last().first)
    }

    @Test
    fun showsSoftLocationPromptWhenPermissionNotDetermined() {
        val location = FakeLocationProvider(status = LocationPermissionStatus.NotDetermined)
        val viewModel = WallboxListViewModel(repository, locationProvider = location)
        assertTrue(viewModel.state.value.showLocationPrompt)
        assertEquals(DEFAULT_CENTER, repository.searches.single().first)
    }

    @Test
    fun allowingLocationRequestsPermissionAndResearches() {
        val location = FakeLocationProvider(
            status = LocationPermissionStatus.NotDetermined,
            location = GeoPoint(lat = 48.1, lng = 11.5),
        )
        location.nextStatusAfterRequest = LocationPermissionStatus.Granted
        val prefs = InMemoryPreferenceStorage()
        val viewModel = WallboxListViewModel(repository, locationProvider = location, preferences = prefs)
        viewModel.onAllowLocation()
        assertEquals(1, location.requestCalls)
        assertEquals(GeoPoint(48.1, 11.5), viewModel.state.value.center)
        assertTrue(viewModel.state.value.usesDeviceLocation)
        assertFalse(viewModel.state.value.showLocationPrompt)
        assertEquals("true", prefs.read("location_prompt_done"))
        assertTrue(repository.searches.any { it.first == GeoPoint(48.1, 11.5) })
    }

    @Test
    fun skippingLocationKeepsBremenAndShowsFallbackHint() {
        val location = FakeLocationProvider(status = LocationPermissionStatus.NotDetermined)
        val prefs = InMemoryPreferenceStorage()
        val viewModel = WallboxListViewModel(repository, locationProvider = location, preferences = prefs)
        viewModel.onSkipLocation()
        assertFalse(viewModel.state.value.showLocationPrompt)
        assertTrue(viewModel.state.value.showLocationFallbackHint)
        assertFalse(viewModel.state.value.usesDeviceLocation)
        assertEquals(DEFAULT_CENTER, viewModel.state.value.center)
        assertEquals(0, location.requestCalls)
        assertEquals("true", prefs.read("location_prompt_done"))
    }

    @Test
    fun deniedPermissionShowsFallbackWithoutPrompt() {
        val location = FakeLocationProvider(status = LocationPermissionStatus.Denied)
        val state = WallboxListViewModel(repository, locationProvider = location).state.value
        assertFalse(state.showLocationPrompt)
        assertTrue(state.showLocationFallbackHint)
        assertEquals(DEFAULT_CENTER, state.center)
    }

    @Test
    fun unsupportedLocationProviderSkipsPromptAndStaysOnBremen() {
        val location = FakeLocationProvider(isSupported = false, status = LocationPermissionStatus.Unavailable)
        val state = WallboxListViewModel(repository, locationProvider = location).state.value
        assertFalse(state.showLocationPrompt)
        assertFalse(state.showLocationFallbackHint)
        assertEquals(DEFAULT_CENTER, state.center)
        assertEquals(0, location.currentCalls)
    }

    @Test
    fun promptNotShownAgainWhenAlreadyAnswered() {
        val location = FakeLocationProvider(status = LocationPermissionStatus.NotDetermined)
        val prefs = InMemoryPreferenceStorage(mapOf("location_prompt_done" to "true"))
        val state = WallboxListViewModel(repository, locationProvider = location, preferences = prefs).state.value
        assertFalse(state.showLocationPrompt)
        assertTrue(state.showLocationFallbackHint)
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

    @Test
    fun detailLoadsHostProfile() {
        repository.hostProfileResult = Outcome.Success(testHostProfile(isSuperhost = true))
        val host = WallboxDetailViewModel("wb-1", repository, FixedClock(), TimeZone.UTC).state.value.host
        assertNotNull(host)
        assertEquals("Andreas M.", host.name)
        assertTrue(host.isSuperhost)
    }

    @Test
    fun detailStaysUsableWhenHostProfileFails() {
        repository.hostProfileResult = failure(AppError.Server)
        val state = WallboxDetailViewModel("wb-1", repository, FixedClock(), TimeZone.UTC).state.value
        assertNotNull(state.wallbox)
        assertNull(state.host)
        assertNull(state.error)
    }
}
