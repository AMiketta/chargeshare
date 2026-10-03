package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.Outcome
import app.chargeshare.presentation.host.CreateWallboxViewModel
import app.chargeshare.presentation.host.HostDashboardViewModel
import app.chargeshare.presentation.host.WallboxField
import app.chargeshare.presentation.host.parseDecimal
import app.chargeshare.presentation.host.parseTime
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.testing.FakeHostRepository
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.testWallbox
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostViewModelsTest {
    private val repository = FakeHostRepository()

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    @Test
    fun dashboardLoadsEarningsAndWallboxes() {
        repository.wallboxesResult = Outcome.Success(listOf(testWallbox("a")))
        val viewModel = HostDashboardViewModel("host-1", repository)
        viewModel.refresh()
        val state = viewModel.state.value
        assertEquals(48.2, state.earnings?.month)
        assertEquals(1, state.wallboxes.size)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun dashboardReportsForbiddenForNonHosts() {
        repository.earningsResult = failure(AppError.Forbidden)
        val viewModel = HostDashboardViewModel("u", repository)
        viewModel.refresh()
        assertEquals(AppError.Forbidden, viewModel.state.value.error)
    }

    @Test
    fun togglingOnlineUpdatesWallbox() {
        repository.wallboxesResult = Outcome.Success(listOf(testWallbox("a", isOnline = true)))
        val viewModel = HostDashboardViewModel("host-1", repository)
        viewModel.refresh()
        viewModel.onOnlineToggled(viewModel.state.value.wallboxes.single(), false)
        assertFalse(viewModel.state.value.wallboxes.single().isOnline)
        assertNull(viewModel.state.value.togglingId)
    }

    @Test
    fun toggleFailureIsShownAndStateKept() {
        repository.wallboxesResult = Outcome.Success(listOf(testWallbox("a", isOnline = true)))
        repository.setOnlineResult = failure(AppError.Network)
        val viewModel = HostDashboardViewModel("host-1", repository)
        viewModel.refresh()
        viewModel.onOnlineToggled(viewModel.state.value.wallboxes.single(), false)
        assertEquals(AppError.Network, viewModel.state.value.toggleError)
        assertTrue(viewModel.state.value.wallboxes.single().isOnline)
    }

    private fun CreateWallboxViewModel.fillValid() {
        onChange { it.copy(name = "Meine Box", address = "Weg 1", city = "Bremen", postalCode = "28195") }
    }

    @Test
    fun emptyFormShowsFieldErrorsAndDoesNotCallServer() {
        val viewModel = CreateWallboxViewModel(repository)
        viewModel.onSubmit()
        val errors = viewModel.state.value.errors
        assertTrue(WallboxField.Name in errors)
        assertTrue(WallboxField.Address in errors)
        assertTrue(repository.createdRequests.isEmpty())
    }

    @Test
    fun validFormSendsRequestWithParsedNumbersAndAvailability() {
        val viewModel = CreateWallboxViewModel(repository)
        viewModel.fillValid()
        viewModel.onChange { it.copy(price = "0,29", powerKw = "7,4", connector = ConnectorType.CCS) }
        viewModel.onSubmit()
        val request = repository.createdRequests.single()
        assertEquals(0.29, request.pricePerKwh)
        assertEquals(7.4, request.powerKw)
        assertEquals(ConnectorType.CCS, request.connectorType)
        assertEquals(listOf("monday", "tuesday", "wednesday", "thursday", "friday"), request.availability.keys.toList())
        assertEquals(listOf(TimeSlot("07:00", "19:00")), request.availability["monday"])
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun invalidNumbersAndTimesAreRejected() {
        val viewModel = CreateWallboxViewModel(repository)
        viewModel.fillValid()
        viewModel.onChange { it.copy(price = "0", lat = "95", availabilityStart = "20:00", availabilityEnd = "08:00") }
        viewModel.onSubmit()
        val errors = viewModel.state.value.errors
        assertTrue(WallboxField.Price in errors)
        assertTrue(WallboxField.Lat in errors)
        assertTrue(WallboxField.AvailabilityEnd in errors)
    }

    @Test
    fun createSendsTrimmedDescriptionAndRejectsImplausibleValues() {
        val viewModel = CreateWallboxViewModel(repository)
        viewModel.fillValid()
        viewModel.onChange { it.copy(description = " Hinterhof ", price = "34", powerKw = "400") }
        viewModel.onSubmit()
        assertTrue(WallboxField.Price in viewModel.state.value.errors)
        assertTrue(WallboxField.PowerKw in viewModel.state.value.errors)
        assertTrue(repository.createdRequests.isEmpty())
        viewModel.onChange { it.copy(price = "0,34", powerKw = "22") }
        viewModel.onSubmit()
        assertEquals("Hinterhof", repository.createdRequests.single().description)
    }

    @Test
    fun noWeekdayIsRejected() {
        val viewModel = CreateWallboxViewModel(repository)
        viewModel.fillValid()
        viewModel.onChange { it.copy(days = emptySet()) }
        viewModel.onSubmit()
        assertTrue(WallboxField.Days in viewModel.state.value.errors)
    }

    @Test
    fun serverErrorOnCreateIsShown() {
        repository.createResult = failure(AppError.Forbidden)
        val viewModel = CreateWallboxViewModel(repository)
        viewModel.fillValid()
        viewModel.onSubmit()
        assertEquals(AppError.Forbidden, viewModel.state.value.submitError)
        assertFalse(viewModel.state.value.saved)
        assertNotNull(viewModel.state.value.name)
    }

    @Test
    fun parsersHandleGermanAndInvalidInput() {
        assertEquals(0.34, parseDecimal("0,34"))
        assertEquals(11.0, parseDecimal(" 11 "))
        assertNull(parseDecimal("abc"))
        assertEquals(9 * 60 + 30, parseTime("09:30"))
        assertNull(parseTime("24:00"))
        assertNull(parseTime("9:5"))
    }
}
