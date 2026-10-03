package app.chargeshare.server

import app.chargeshare.server.config.AppConfig
import app.chargeshare.server.db.DevSeed
import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.WallboxSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DevSeedTest {
    @Test
    fun seedCreatesBremenExampleDataIdempotently() = serverTest(config = testConfig.copy(devSeed = true)) {
        val c = newApi()
        val boxes = c.api.searchWallboxes(WallboxSearch(lat = 53.0758, lng = 8.8072, radiusM = 20_000.0, limit = 50)).wallboxes
        assertEquals(8, boxes.size)
        assertTrue(boxes.all { it.name.startsWith("[Beispiel]") && it.isOnline && it.city == "Bremen" })
        assertEquals("[Beispiel] Marktplatz", boxes.first().name)
        // Demo-Logins funktionieren
        assertEquals("Demo Host (Beispieldaten)", c.api.login(DevSeed.HOST_EMAIL, DevSeed.PASSWORD).user!!.name)
        newApi().api.login(DevSeed.DRIVER_EMAIL, DevSeed.PASSWORD)
        // zweiter Lauf legt nichts doppelt an
        DevSeed.run(app.chargeshare.server.db.Db(TestDb.database), 4)
        assertEquals(8, c.api.searchWallboxes(WallboxSearch(limit = 50)).wallboxes.size)
    }

    @Test
    fun seedIsOffByDefaultAndForbiddenInProduction() = serverTest {
        assertEquals(0, newApi().api.searchWallboxes().wallboxes.size)
        assertFailsWith<ApiException> { newApi().api.login(DevSeed.HOST_EMAIL, DevSeed.PASSWORD) }
        assertTrue(runCatching { AppConfig(env = "production", jwtSecret = "s".repeat(40), jwtRefreshSecret = "r".repeat(40), devSeed = true) }.isFailure)
        assertTrue(AppConfig.fromEnv(mapOf("ENV" to "development")).devSeed)
        assertEquals(false, AppConfig.fromEnv(mapOf("ENV" to "development", "DEV_SEED" to "false")).devSeed)
        assertEquals(false, AppConfig.fromEnv(mapOf("ENV" to "staging")).devSeed)
    }
}
