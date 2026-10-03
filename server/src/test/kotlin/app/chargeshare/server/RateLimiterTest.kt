package app.chargeshare.server

import app.chargeshare.server.plugins.RateLimiter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RateLimiterTest {
    private var now = 1_000_000L

    private fun limiter(limit: Int = 3, maxKeys: Int = 1000) = RateLimiter(limit, 1.minutes, { now }, maxKeys)

    @Test
    fun allowsUpToTheLimitThenBlocksWithRetryAfter() {
        val limiter = limiter()
        repeat(3) { assertTrue(limiter.tryAcquire("a").allowed) }
        now += 20_000
        val blocked = limiter.tryAcquire("a")
        assertFalse(blocked.allowed)
        assertEquals(40, blocked.retryAfterSeconds)
    }

    @Test
    fun windowResetsAfterItsLength() {
        val limiter = limiter()
        repeat(3) { limiter.tryAcquire("a") }
        assertFalse(limiter.tryAcquire("a").allowed)
        now += 60_000
        assertTrue(limiter.tryAcquire("a").allowed)
    }

    @Test
    fun keysAreIndependent() {
        val limiter = limiter(limit = 1)
        assertTrue(limiter.tryAcquire("a").allowed)
        assertFalse(limiter.tryAcquire("a").allowed)
        assertTrue(limiter.tryAcquire("b").allowed)
    }

    @Test
    fun blockedAttemptsDoNotExtendTheWindow() {
        val limiter = limiter(limit = 1)
        limiter.tryAcquire("a")
        repeat(10) {
            now += 5_000
            assertFalse(limiter.tryAcquire("a").allowed)
        }
        now += 10_000
        assertTrue(limiter.tryAcquire("a").allowed)
    }

    @Test
    fun fullTableDoesNotLockOutNewKeys() {
        val limiter = limiter(limit = 1, maxKeys = 2)
        limiter.tryAcquire("a")
        limiter.tryAcquire("b")
        // unbekannter Schlüssel bei voller Tabelle: erlaubt (und nicht gezählt)
        repeat(3) { assertTrue(limiter.tryAcquire("c").allowed) }
        // abgelaufene Fenster werden entfernt, danach ist wieder Platz
        now += 61_000
        assertTrue(limiter.tryAcquire("c").allowed)
        assertFalse(limiter.tryAcquire("c").allowed)
    }

    @Test
    fun invalidArgumentsAreRejected() {
        assertFailsWith<IllegalArgumentException> { RateLimiter(0, 1.minutes) }
        assertFailsWith<IllegalArgumentException> { RateLimiter(1, 0.seconds) }
    }
}
