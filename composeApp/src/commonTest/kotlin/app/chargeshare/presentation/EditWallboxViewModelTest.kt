package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.presentation.host.EditWallboxViewModel
import app.chargeshare.presentation.host.WallboxField
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.shared.model.WallboxLimits
import app.chargeshare.testing.FakeHostRepository
import app.chargeshare.testing.FixedClock
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.testWallbox
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class EditWallboxViewModelTest {
    private val repository = FakeHostRepository()
    private val weekdays = listOf("monday", "tuesday", "wednesday", "thursday", "friday")
    private val workdaySlot = TimeSlot("08:00", "16:30")
    private val wallbox: Wallbox = testWallbox("wb-7", pricePerKwh = 0.34, powerKw = 11.0).copy(
        description = "Einfahrt links",
        isMetered = true,
        availability = weekdays.associateWith { listOf(workdaySlot) },
    )

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    @Test
    fun formStartsWithCurrentValuesInGermanNotation() {
        val state = EditWallboxViewModel(wallbox, repository, TimeZone.UTC).state.value
        assertEquals("11", state.powerKw)
        assertEquals("0,34", state.price)
        assertEquals("Einfahrt links", state.description)
        assertEquals("08:00", state.availabilityStart)
        assertEquals("16:30", state.availabilityEnd)
        assertEquals(weekdays.toSet(), state.days)
        assertTrue(state.availabilityEditable)
    }

    @Test
    fun priceChangeSendsUpdateWithoutAvailability() {
        val viewModel = EditWallboxViewModel(wallbox, repository, TimeZone.UTC)
        viewModel.onChange { it.copy(price = "0,29", description = "  Tor ist offen ") }
        viewModel.onSubmit()
        val (id, request) = repository.updatedRequests.single()
        assertEquals("wb-7", id)
        assertEquals(0.29, request.pricePerKwh)
        assertEquals("Tor ist offen", request.description)
        assertNull(request.availability)
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun clearingTheDescriptionSendsEmptyText() {
        val viewModel = EditWallboxViewModel(wallbox, repository, TimeZone.UTC)
        viewModel.onChange { it.copy(description = "   ") }
        viewModel.onSubmit()
        assertEquals("", repository.updatedRequests.single().second.description)
    }

    @Test
    fun changedAvailabilityIsSent() {
        val viewModel = EditWallboxViewModel(wallbox, repository, TimeZone.UTC)
        viewModel.onChange { it.copy(availabilityEnd = "18:00") }
        viewModel.onDayToggled("saturday")
        viewModel.onSubmit()
        val availability = repository.updatedRequests.single().second.availability
        assertEquals(listOf(TimeSlot("08:00", "18:00")), availability?.get("saturday"))
        assertEquals(6, availability?.size)
    }

    @Test
    fun invalidAvailabilityIsOnlyCheckedWhenChanged() {
        val viewModel = EditWallboxViewModel(wallbox, repository, TimeZone.UTC)
        viewModel.onChange { it.copy(availabilityEnd = "07:00") }
        viewModel.onSubmit()
        assertTrue(WallboxField.AvailabilityEnd in viewModel.state.value.errors)
        assertTrue(repository.updatedRequests.isEmpty())
    }

    @Test
    fun unrepresentableWeekPlanIsNeverSent() {
        val irregular = wallbox.copy(availability = mapOf("monday" to listOf(TimeSlot("08:00", "10:00")), "tuesday" to listOf(TimeSlot("09:00", "12:00"))))
        val viewModel = EditWallboxViewModel(irregular, repository, TimeZone.UTC)
        assertFalse(viewModel.state.value.availabilityEditable)
        viewModel.onChange { it.copy(price = "0,50") }
        viewModel.onSubmit()
        val request = repository.updatedRequests.single().second
        assertEquals(0.5, request.pricePerKwh)
        assertNull(request.availability)
    }

    @Test
    fun emptyWeekPlanStaysUntouchedUnlessEdited() {
        val viewModel = EditWallboxViewModel(wallbox.copy(availability = emptyMap()), repository)
        assertTrue(viewModel.state.value.availabilityEditable)
        assertTrue(viewModel.state.value.days.isEmpty())
        viewModel.onChange { it.copy(name = "Neu") }
        viewModel.onSubmit()
        assertNull(repository.updatedRequests.single().second.availability)
    }

    @Test
    fun limitsAreEnforcedBeforeCallingTheServer() {
        val viewModel = EditWallboxViewModel(wallbox, repository, TimeZone.UTC)
        viewModel.onChange {
            it.copy(
                name = " ",
                price = "34",
                powerKw = "500",
                description = "x".repeat(WallboxLimits.MAX_DESCRIPTION_LENGTH + 1),
                address = "",
            )
        }
        viewModel.onSubmit()
        val errors = viewModel.state.value.errors
        assertEquals(
            setOf(WallboxField.Name, WallboxField.Price, WallboxField.PowerKw, WallboxField.Description, WallboxField.Address),
            errors,
        )
        assertTrue(repository.updatedRequests.isEmpty())
    }

    @Test
    fun connectorChangeIsSent() {
        val viewModel = EditWallboxViewModel(wallbox, repository, TimeZone.UTC)
        viewModel.onChange { it.copy(connector = ConnectorType.CCS) }
        viewModel.onSubmit()
        assertEquals(ConnectorType.CCS, repository.updatedRequests.single().second.connectorType)
    }

    @Test
    fun forbiddenFromServerIsShownAndNotSaved() {
        repository.updateResult = failure(AppError.Forbidden)
        val viewModel = EditWallboxViewModel(wallbox, repository, TimeZone.UTC)
        viewModel.onSubmit()
        assertEquals(AppError.Forbidden, viewModel.state.value.submitError)
        assertFalse(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun berlinTimesAreShownLocallyAndSentAsUtc() {
        val berlin = TimeZone.of("Europe/Berlin")
        val summer = Instant.parse("2026-07-15T10:00:00Z")
        val winter = Instant.parse("2026-12-15T10:00:00Z")
        // Server speichert UTC 06:00–14:30 → im Sommer 08:00–16:30, im Winter 07:00–15:30 Ortszeit
        val stored = wallbox.copy(availability = weekdays.associateWith { listOf(TimeSlot("06:00", "14:30")) })
        val inSummer = EditWallboxViewModel(stored, repository, berlin, FixedClock(summer))
        assertEquals("08:00", inSummer.state.value.availabilityStart)
        assertEquals("16:30", inSummer.state.value.availabilityEnd)
        val inWinter = EditWallboxViewModel(stored, repository, berlin, FixedClock(winter))
        assertEquals("07:00", inWinter.state.value.availabilityStart)

        inSummer.onChange { it.copy(availabilityStart = "09:00") }
        inSummer.onSubmit()
        val sent = repository.updatedRequests.single().second.availability
        assertEquals(listOf(TimeSlot("07:00", "14:30")), sent?.get("monday"))
    }

    @Test
    fun unchangedAvailabilityIsNotResentEvenWithTimeZoneConversion() {
        val berlin = TimeZone.of("Europe/Berlin")
        val stored = wallbox.copy(availability = weekdays.associateWith { listOf(TimeSlot("06:00", "14:30")) })
        val viewModel = EditWallboxViewModel(stored, repository, berlin, FixedClock(Instant.parse("2026-07-15T10:00:00Z")))
        viewModel.onChange { it.copy(price = "0,40") }
        viewModel.onSubmit()
        assertNull(repository.updatedRequests.single().second.availability)
    }
}
