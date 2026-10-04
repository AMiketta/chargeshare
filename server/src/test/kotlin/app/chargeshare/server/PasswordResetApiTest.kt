package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** „Passwort vergessen“: Token mit Ablauf, Einmalverwendung, keine Auskunft über Konten, Mail über den austauschbaren Mailer. */
class PasswordResetApiTest {

    private fun tokenFrom(mail: String): String = Regex("token=([A-Za-z0-9_-]+)").find(mail)!!.groupValues[1]

    private fun expireAllTokens() = transaction(TestDb.database) {
        exec("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'")
    }

    @Test
    fun resetFlowChangesPasswordAndSignsOutOtherDevices() = serverTest {
        val user = registerUser("ada@example.com")
        val other = newApi().also { it.api.login("ada@example.com", "geheim1234") }

        user.api.forgotPassword("Ada@Example.com")

        val mail = mailer.sent.single()
        assertEquals("ada@example.com", mail.to)
        assertTrue("http://localhost/reset-password?token=" in mail.body)
        user.api.resetPassword(tokenFrom(mail.body), "neues-passwort-99")

        val fresh = newApi()
        assertFailsWith<ApiException> { fresh.api.login("ada@example.com", "geheim1234") }
        fresh.api.login("ada@example.com", "neues-passwort-99")
        // Refresh-Tokens aller Geräte sind widerrufen
        assertFailsWith<ApiException> { other.api.refresh(other.store.refreshToken()!!) }
    }

    @Test
    fun unknownAddressGetsTheSameAnswerAndNoMail() = serverTest {
        val unknown = newApi()
        unknown.api.forgotPassword("niemand@example.com")
        unknown.api.forgotPassword("kein-email")
        assertTrue(mailer.sent.isEmpty())
    }

    @Test
    fun tokenWorksOnlyOnce() = serverTest {
        val user = registerUser("ada@example.com")
        user.api.forgotPassword("ada@example.com")
        val token = tokenFrom(mailer.sent.single().body)

        user.api.resetPassword(token, "neues-passwort-99")
        val e = assertFailsWith<ApiException> { user.api.resetPassword(token, "noch-ein-passwort-1") }
        assertEquals(422, e.status)
        newApi().api.login("ada@example.com", "neues-passwort-99")
    }

    @Test
    fun expiredOrInvalidTokenIsRejected() = serverTest {
        val user = registerUser("ada@example.com")
        user.api.forgotPassword("ada@example.com")
        val token = tokenFrom(mailer.sent.single().body)

        assertEquals(422, assertFailsWith<ApiException> { user.api.resetPassword("falsch", "neues-passwort-99") }.status)
        expireAllTokens()
        assertEquals(422, assertFailsWith<ApiException> { user.api.resetPassword(token, "neues-passwort-99") }.status)
        newApi().api.login("ada@example.com", "geheim1234")
    }

    @Test
    fun newRequestInvalidatesTheOldToken() = serverTest {
        val user = registerUser("ada@example.com")
        user.api.forgotPassword("ada@example.com")
        user.api.forgotPassword("ada@example.com")
        val (first, second) = mailer.sent.map { tokenFrom(it.body) }
        assertNotEquals(first, second)

        assertFailsWith<ApiException> { user.api.resetPassword(first, "neues-passwort-99") }
        user.api.resetPassword(second, "neues-passwort-99")
    }

    @Test
    fun tooShortPasswordIsRejectedAndKeepsTokenUsable() = serverTest {
        val user = registerUser("ada@example.com")
        user.api.forgotPassword("ada@example.com")
        val token = tokenFrom(mailer.sent.single().body)

        assertEquals(422, assertFailsWith<ApiException> { user.api.resetPassword(token, "kurz") }.status)
        user.api.resetPassword(token, "neues-passwort-99")
    }

    @Test
    fun mailerFailureIsInvisibleToTheCaller() = serverTest {
        val user = registerUser("ada@example.com")
        mailer.failure = IllegalStateException("smtp down")
        user.api.forgotPassword("ada@example.com")
        assertTrue(mailer.sent.isEmpty())
    }

    @Test
    fun tokenIsStoredOnlyAsHash() = serverTest {
        val user = registerUser("ada@example.com")
        user.api.forgotPassword("ada@example.com")
        val token = tokenFrom(mailer.sent.single().body)
        var stored = ""
        transaction(TestDb.database) { exec("SELECT token_hash FROM password_reset_tokens") { if (it.next()) stored = it.getString(1) } }
        assertNotEquals(token, stored)
        assertEquals(64, stored.length)
    }

    @Test
    fun publicBaseUrlIsUsedInTheLink() = serverTest(config = testConfig.copy(publicBaseUrl = "https://api.chargeshare.app")) {
        registerUser("ada@example.com").api.forgotPassword("ada@example.com")
        assertTrue(mailer.sent.single().body.contains("https://api.chargeshare.app/reset-password?token="))
    }

    @Test
    fun resetPageRedirectsIntoTheAppAndRejectsMarkup() = serverTest {
        val ok = raw.get("/reset-password?token=abcdefghijklmnopqrstuvwxyz0123456789")
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue("chargeshare://reset-password?token=abcdefghijklmnopqrstuvwxyz0123456789" in ok.bodyAsText())

        val bad = raw.get("/reset-password?token=%22%3E%3Cscript%3Ealert(1)%3C/script%3E")
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertFalse("script" in bad.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, raw.get("/reset-password").status)
    }

    @Test
    fun forgotPasswordIsRateLimited() = serverTest(config = testConfig.copy(authRateLimitPerWindow = 2)) {
        val client = newApi()
        client.api.forgotPassword("a@example.com")
        client.api.forgotPassword("a@example.com")
        assertEquals(429, assertFailsWith<ApiException> { client.api.forgotPassword("a@example.com") }.status)
    }
}
