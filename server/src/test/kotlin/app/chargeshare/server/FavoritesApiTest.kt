package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Favoriten (Herz): `GET|PUT|DELETE /users/me/favorites`. */
class FavoritesApiTest {

    @Test
    fun newUserHasNoFavorites() = serverTest {
        assertTrue(registerUser("neu@example.com").api.favoriteIds().isEmpty())
    }

    @Test
    fun addListAndRemoveFavorite() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val first = host.api.createWallbox(newWallbox(name = "Eins"))
        val second = host.api.createWallbox(newWallbox(name = "Zwei"))
        val driver = registerUser("fahrer@example.com")

        driver.api.addFavorite(first.id)
        driver.api.addFavorite(second.id)
        assertEquals(listOf(second.id, first.id), driver.api.favoriteIds(), "neueste Favoriten zuerst")

        driver.api.removeFavorite(first.id)
        assertEquals(listOf(second.id), driver.api.favoriteIds())
    }

    @Test
    fun addingTwiceAndRemovingUnknownAreHarmless() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox())
        val driver = registerUser("fahrer@example.com")

        driver.api.addFavorite(wallbox.id)
        driver.api.addFavorite(wallbox.id)
        assertEquals(listOf(wallbox.id), driver.api.favoriteIds())

        driver.api.removeFavorite(wallbox.id)
        driver.api.removeFavorite(wallbox.id)
        assertTrue(driver.api.favoriteIds().isEmpty())
    }

    @Test
    fun favoritesBelongToTheUser() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox())
        val driver = registerUser("fahrer@example.com")
        val other = registerUser("anderer@example.com")

        driver.api.addFavorite(wallbox.id)
        assertTrue(other.api.favoriteIds().isEmpty())
    }

    @Test
    fun unknownWallboxIsNotFound() = serverTest {
        val driver = registerUser("fahrer@example.com")
        val error = assertFailsWith<ApiException> { driver.api.addFavorite("00000000-0000-0000-0000-000000000000") }
        assertEquals(404, error.status)
    }

    @Test
    fun invalidIdIsRejected() = serverTest {
        val driver = registerUser("fahrer@example.com")
        assertEquals(400, assertFailsWith<ApiException> { driver.api.addFavorite("kein-uuid") }.status)
    }

    @Test
    fun favoritesRequireLogin() = serverTest {
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.favoriteIds() }.status)
    }

    @Test
    fun favoritesDisappearWithWallboxAndAccount() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox())
        val driver = registerUser("fahrer@example.com")
        driver.api.addFavorite(wallbox.id)

        host.api.deleteWallbox(wallbox.id)
        assertTrue(driver.api.favoriteIds().isEmpty(), "gelöschte Wallbox ist kein Favorit mehr")

        val kept = host.api.createWallbox(newWallbox(name = "Neu"))
        driver.api.addFavorite(kept.id)
        driver.api.deleteMe()
    }
}
