package app.chargeshare.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SplashTest {
    @Test
    fun splashStaysWhileAnimationRuns() {
        assertFalse(shouldDismissSplash(animationDone = false, sessionRestoring = false))
    }

    @Test
    fun splashWaitsForSessionRestore() {
        assertFalse(shouldDismissSplash(animationDone = true, sessionRestoring = true))
    }

    @Test
    fun splashGoesWhenAnimationDoneAndSessionKnown() {
        assertTrue(shouldDismissSplash(animationDone = true, sessionRestoring = false))
    }

    @Test
    fun lightAndDarkUseDifferentBrandColors() {
        assertEquals(SplashColors.LightBackground, SplashColors.background(isDark = false))
        assertEquals(SplashColors.DarkBackground, SplashColors.background(isDark = true))
        assertNotEquals(SplashColors.background(false), SplashColors.background(true))
        assertNotEquals(SplashColors.bolt(false), SplashColors.bolt(true))
    }
}
