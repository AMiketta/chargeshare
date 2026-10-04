package app.chargeshare.presentation

import app.chargeshare.data.InMemoryPreferenceStorage
import app.chargeshare.presentation.onboarding.OnboardingPage
import app.chargeshare.presentation.onboarding.OnboardingSettings
import app.chargeshare.ui.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OnboardingSettingsTest {
    private val storage = InMemoryPreferenceStorage()

    @Test
    fun firstStartShowsTheIntro() {
        assertTrue(OnboardingSettings(storage).visible.value)
    }

    @Test
    fun finishingHidesItAndRemembersLocally() {
        val settings = OnboardingSettings(storage)
        settings.finish()
        assertFalse(settings.visible.value)
        // Neuer Start (neues Objekt, gleicher Speicher): nicht mehr automatisch.
        assertFalse(OnboardingSettings(storage).visible.value)
    }

    @Test
    fun replayShowsItAgainWithoutResettingTheFlag() {
        val settings = OnboardingSettings(storage)
        settings.finish()
        settings.show()
        assertTrue(settings.visible.value)
        // Wurde die App währenddessen beendet, kommt sie nicht wieder von selbst.
        assertFalse(OnboardingSettings(storage).visible.value)
        settings.finish()
        assertFalse(settings.visible.value)
    }

    @Test
    fun unrelatedStoredValueCountsAsNotSeen() {
        val settings = OnboardingSettings(InMemoryPreferenceStorage(mapOf("onboarding_seen" to "kaputt")))
        assertTrue(settings.visible.value)
    }

    @Test
    fun introHasFourPagesInOrderWithOwnTexts() {
        assertEquals(listOf(OnboardingPage.Find, OnboardingPage.Book, OnboardingPage.Charge, OnboardingPage.Host), OnboardingPage.entries)
        val titles = OnboardingPage.entries.map(Strings::onboardingTitle)
        assertEquals(titles.size, titles.toSet().size)
        assertTrue(OnboardingPage.entries.all { Strings.onboardingBody(it).isNotBlank() })
        assertNotEquals(Strings.onboardingTitle(OnboardingPage.Find), Strings.onboardingBody(OnboardingPage.Find))
        assertEquals("Seite 2 von 4", Strings.onboardingProgress(2, 4))
    }
}
