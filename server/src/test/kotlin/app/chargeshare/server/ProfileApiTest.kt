package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.ProfileLimits
import app.chargeshare.shared.model.UpdateUserRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Profil bearbeiten (`PUT /users/me`): Name und Telefon mit Validierung. */
class ProfileApiTest {

    @Test
    fun nameAndPhoneAreUpdatedAndTrimmed() = serverTest {
        val user = registerUser("ada@example.com")
        val updated = user.api.updateMe(UpdateUserRequest(name = "  Ada Lovelace ", phone = " +49 421 1234567 "))
        assertEquals("Ada Lovelace", updated.name)
        assertEquals("+49 421 1234567", updated.phone)
        assertEquals("Ada Lovelace", user.api.me().name)
    }

    @Test
    fun partialUpdateKeepsOtherFields() = serverTest {
        val user = registerUser("ada@example.com")
        user.api.updateMe(UpdateUserRequest(phone = "0421 1234567"))
        val renamed = user.api.updateMe(UpdateUserRequest(name = "Ada L."))
        assertEquals("Ada L.", renamed.name)
        assertEquals("0421 1234567", renamed.phone)
    }

    @Test
    fun emptyPhoneRemovesTheNumber() = serverTest {
        val user = registerUser("ada@example.com")
        user.api.updateMe(UpdateUserRequest(phone = "0421 1234567"))
        assertNull(user.api.updateMe(UpdateUserRequest(phone = "  ")).phone)
        assertNull(user.api.me().phone)
    }

    @Test
    fun invalidNameIsRejected() = serverTest {
        val user = registerUser("ada@example.com")
        assertEquals(422, assertFailsWith<ApiException> { user.api.updateMe(UpdateUserRequest(name = "   ")) }.status)
        val tooLong = "x".repeat(ProfileLimits.MAX_NAME_LENGTH + 1)
        assertEquals(422, assertFailsWith<ApiException> { user.api.updateMe(UpdateUserRequest(name = tooLong)) }.status)
        assertEquals("ada", user.api.me().name, "abgelehnte Änderungen werden nicht gespeichert")
    }

    @Test
    fun invalidPhoneIsRejected() = serverTest {
        val user = registerUser("ada@example.com")
        listOf("abc", "12345", "<script>", "+49 421 12345678901234567890").forEach { phone ->
            assertEquals(422, assertFailsWith<ApiException> { user.api.updateMe(UpdateUserRequest(phone = phone)) }.status, phone)
        }
        assertNull(user.api.me().phone)
    }

    @Test
    fun updatingRequiresLogin() = serverTest {
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.updateMe(UpdateUserRequest(name = "Ada")) }.status)
    }
}
