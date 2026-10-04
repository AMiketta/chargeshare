package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.presentation.host.CreateWallboxViewModel
import app.chargeshare.presentation.host.EditWallboxViewModel
import app.chargeshare.presentation.host.GeocodeStatus
import app.chargeshare.presentation.host.WallboxField
import app.chargeshare.shared.model.GeocodeResponse
import app.chargeshare.testing.FakeHostRepository
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.testWallbox
import app.chargeshare.data.Outcome
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeocodeFormTest {
    private val repository = FakeHostRepository()

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    private fun filledForm(): CreateWallboxViewModel = CreateWallboxViewModel(repository).also {
        it.onChange { s -> s.copy(address = " Musterstraße 1 ", postalCode = "10115", city = "Berlin") }
    }

    @Test
    fun foundAddressFillsCoordinatesRoundedToSixDecimals() {
        repository.geocodeResult = Outcome.Success(GeocodeResponse(52.52000659, 13.40495412, "Musterstraße 1, Berlin"))
        val viewModel = filledForm()
        viewModel.onGeocode()
        val state = viewModel.state.value
        assertEquals(Triple("Musterstraße 1", "10115", "Berlin"), repository.geocodeRequests.single())
        assertEquals("52.520007", state.lat)
        assertEquals("13.404954", state.lng)
        assertEquals(GeocodeStatus.Found("Musterstraße 1, Berlin"), state.geocode)
    }

    @Test
    fun foundAddressClearsCoordinateErrors() {
        val viewModel = filledForm()
        viewModel.onChange { it.copy(lat = "x", lng = "y") }
        viewModel.onSubmit()
        assertTrue(WallboxField.Lat in viewModel.state.value.errors)
        viewModel.onGeocode()
        assertTrue(WallboxField.Lat !in viewModel.state.value.errors)
        assertTrue(WallboxField.Lng !in viewModel.state.value.errors)
    }

    @Test
    fun incompleteAddressDoesNotCallTheServer() {
        val viewModel = CreateWallboxViewModel(repository)
        viewModel.onChange { it.copy(address = "Musterstraße 1") }
        viewModel.onGeocode()
        assertEquals(GeocodeStatus.AddressIncomplete, viewModel.state.value.geocode)
        assertTrue(repository.geocodeRequests.isEmpty())
    }

    @Test
    fun unknownAddressKeepsCoordinates() {
        repository.geocodeResult = failure(AppError.NotFound)
        val viewModel = filledForm()
        val before = viewModel.state.value
        viewModel.onGeocode()
        assertEquals(GeocodeStatus.NotFound, viewModel.state.value.geocode)
        assertEquals(before.lat, viewModel.state.value.lat)
        assertEquals(before.lng, viewModel.state.value.lng)
    }

    @Test
    fun unavailableServiceIsReportedAsUnavailable() {
        repository.geocodeResult = failure(AppError.Server)
        val viewModel = filledForm()
        viewModel.onGeocode()
        assertEquals(GeocodeStatus.Unavailable, viewModel.state.value.geocode)
    }

    @Test
    fun rateLimitIsKeptAsFailure() {
        repository.geocodeResult = failure(AppError.RateLimited)
        val viewModel = filledForm()
        viewModel.onGeocode()
        assertEquals(GeocodeStatus.Failed(AppError.RateLimited), viewModel.state.value.geocode)
    }

    @Test
    fun editingTheFormResetsTheSearchResult() {
        val viewModel = filledForm()
        viewModel.onGeocode()
        assertTrue(viewModel.state.value.geocode is GeocodeStatus.Found)
        viewModel.onChange { it.copy(city = "Hamburg") }
        assertEquals(GeocodeStatus.Idle, viewModel.state.value.geocode)
    }

    @Test
    fun editFormGeocodesToo() {
        val viewModel = EditWallboxViewModel(testWallbox("wb-1"), repository, TimeZone.UTC)
        viewModel.onGeocode()
        assertTrue(viewModel.state.value.geocode is GeocodeStatus.Found)
        assertEquals("52.520007", viewModel.state.value.lat)
    }
}
