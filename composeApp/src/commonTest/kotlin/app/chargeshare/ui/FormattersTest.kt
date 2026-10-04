package app.chargeshare.ui

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
}
