package app.chargeshare.ui

import app.chargeshare.shared.model.TimeSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class FormattersTest {
    @Test
    fun eurosUseGermanDecimalCommaAndSymbolAfterAmount() {
        assertEquals("11,22 €", formatEuro(11.22))
        assertEquals("0,05 €", formatEuro(0.05))
        assertEquals("7,00 €", formatEuro(7.0))
        assertEquals("0,34 €/kWh", formatPricePerKwh(0.34))
    }

    @Test
    fun powerOmitsUselessDecimal() {
        assertEquals("11 kW", formatPowerKw(11.0))
        assertEquals("7,4 kW", formatPowerKw(7.4))
    }

    @Test
    fun compactKwhDropsDecimalFromHundredKwh() {
        assertEquals("423 kWh", formatKwhShort(423.0))
        assertEquals("15,8 kWh", formatKwhShort(15.8))
        assertEquals("0,0 kWh", formatKwhShort(0.0))
    }

    @Test
    fun distanceSwitchesFromMetersToKilometers() {
        assertEquals("200 m", formatDistance(200.0))
        assertEquals("1,2 km", formatDistance(1_240.0))
        assertEquals("5 km", formatRadius(5_000.0))
    }

    @Test
    fun timeIsTwentyFourHourInGivenZone() {
        val instant = Instant.parse("2026-10-05T08:05:00Z")
        assertEquals("08:05", formatTime(instant, TimeZone.UTC))
        assertEquals("10:05", formatTime(instant, TimeZone.of("Europe/Berlin")))
    }

    @Test
    fun dateIsShortGerman() {
        assertEquals("Mo, 5. Okt.", formatDate(LocalDate(2026, 10, 5)))
    }

    @Test
    fun weekdayKeysAndInitials() {
        assertEquals("Mo", weekdayShortFromKey("monday"))
        assertEquals(listOf("monday", "friday", "sunday"), listOf("sunday", "monday", "friday").sortedBy(::weekdayIndexFromKey))
        assertEquals("AM", initialsOf("Andreas Miketta"))
        assertEquals("?", initialsOf(" "))
    }

    @Test
    fun durationLabelsAreGerman() {
        assertEquals("30 Min.", Strings.durationLabel(30))
        assertEquals("2 Std.", Strings.durationLabel(120))
        assertEquals("1 Std. 30 Min.", Strings.durationLabel(90))
    }

    @Test
    fun `Kostenzeile zeigt kWh mit einer Nachkommastelle`() {
        assertEquals("ca. 6,4 kWh × 0,39 €", Strings.estimateLine(6.4, 0.39))
        assertEquals("ca. 5,5 kWh × 0,39 €", Strings.estimateLine(5.5, 0.39))
    }

    @Test
    fun openingHoursDropUselessZerosAndMinutes() {
        assertEquals("9–20 Uhr", formatOpeningHours(TimeSlot("09:00", "20:00")))
        assertEquals("9:30–20:15 Uhr", formatOpeningHours(TimeSlot("09:30", "20:15")))
        assertEquals("0–24 Uhr", formatOpeningHours(TimeSlot("00:00", "24:00")))
    }

    @Test
    fun modelBadgeCombinesManufacturerModelAndPower() {
        assertEquals("KEBA P30 · 11 kW", Strings.modelBadge("KEBA", "P30", 11.0))
        assertEquals("P30 · 7,4 kW", Strings.modelBadge(null, "P30", 7.4))
        assertEquals("KEBA · 11 kW", Strings.modelBadge("KEBA", null, 11.0))
        assertEquals("11 kW", Strings.modelBadge(null, null, 11.0))
    }

    @Test
    fun hostSummaryPluralizesSessions() {
        assertEquals("Mitglied seit 2024 · 47 Sessions", Strings.hostSummary(2024, 47))
        assertEquals("Mitglied seit 2026 · 1 Session", Strings.hostSummary(2026, 1))
        assertEquals("Mitglied seit 2026 · 0 Sessions", Strings.hostSummary(2026, 0))
    }
}
