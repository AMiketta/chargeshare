package app.chargeshare.presentation

import app.chargeshare.presentation.host.DayPart
import app.chargeshare.presentation.host.EMPTY_BAR
import app.chargeshare.presentation.host.MIN_BAR
import app.chargeshare.presentation.host.barFractions
import app.chargeshare.presentation.host.dayPartFor
import app.chargeshare.presentation.host.shortName
import app.chargeshare.presentation.host.todayIndex
import app.chargeshare.shared.model.DailyEarnings
import app.chargeshare.ui.Strings
import app.chargeshare.ui.monthName
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardFormatTest {
    @Test
    fun greetingFollowsTheHour() {
        assertEquals(DayPart.MORNING, dayPartFor(5))
        assertEquals(DayPart.MORNING, dayPartFor(10))
        assertEquals(DayPart.DAY, dayPartFor(11))
        assertEquals(DayPart.DAY, dayPartFor(17))
        assertEquals(DayPart.EVENING, dayPartFor(18))
        assertEquals(DayPart.EVENING, dayPartFor(23))
        assertEquals(DayPart.EVENING, dayPartFor(2))
    }

    @Test
    fun barsAreScaledToTheMaximum() {
        assertEquals(listOf(0.5f, 1f, EMPTY_BAR), barFractions(listOf(5.0, 10.0, 0.0)))
    }

    @Test
    fun tinyValuesStayVisibleAndEmptyWeekIsAllStubs() {
        assertEquals(listOf(MIN_BAR, 1f), barFractions(listOf(0.01, 100.0)))
        assertEquals(List(7) { EMPTY_BAR }, barFractions(List(7) { 0.0 }))
        assertEquals(emptyList(), barFractions(emptyList()))
    }

    @Test
    fun todayIsFoundInTheWeek() {
        val week = listOf("2026-10-05", "2026-10-06", "2026-10-07").map { DailyEarnings(it, 0.0) }
        assertEquals(1, todayIndex(week, LocalDate(2026, 10, 6)))
        assertEquals(-1, todayIndex(week, LocalDate(2026, 10, 20)))
        assertEquals(-1, todayIndex(week, null))
    }

    @Test
    fun namesAreShortened() {
        assertEquals("Andreas M.", shortName("Andreas Miketta"))
        assertEquals("Anna K.", shortName("  Anna Maria  Kowalski "))
        assertEquals("Demo H.", shortName("Demo Host (Beispieldaten)"))
        assertEquals("Andreas", shortName("Andreas"))
        assertEquals("", shortName(""))
    }

    @Test
    fun deltaTextHasSignAndMonth() {
        assertEquals("+12 % vs. März", Strings.earningsDelta(12.4, monthName(Month.MARCH)))
        assertEquals("−5 % vs. Dezember", Strings.earningsDelta(-4.6, monthName(Month.DECEMBER)))
        assertEquals("+0 % vs. Mai", Strings.earningsDelta(0.0, monthName(Month.MAY)))
        assertEquals("Oktober 2026 – Einnahmen", Strings.earningsLabel(monthName(Month.OCTOBER), 2026))
    }

    @Test
    fun subtitleMentionsCityAndCount() {
        assertEquals("Deine Wallbox ist aktiv · Bremen", Strings.hostSubtitle(1, "Bremen"))
        assertEquals("2 Wallboxen aktiv · Bremen", Strings.hostSubtitle(2, "Bremen"))
    }

    @Test
    fun chartDescriptionListsEveryDay() {
        val text = Strings.weekChartDescription(listOf("Mo" to 1.5, "Di" to 0.0))
        assertEquals("Wochenübersicht: Mo 1,50 €, Di 0,00 €", text)
    }
}
