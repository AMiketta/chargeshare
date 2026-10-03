package app.chargeshare.server

import app.chargeshare.server.auth.AppleJwksTokenVerifier
import app.chargeshare.server.auth.TokenService
import app.chargeshare.server.db.RefreshTokens
import app.chargeshare.server.db.Users
import app.chargeshare.server.services.AuthService
import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.AppleAuthRequest
import app.chargeshare.shared.model.ErrorCodes
import app.chargeshare.shared.model.UpdateUserRequest
import app.chargeshare.shared.model.UserRole
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class AuthApiTest {

    @Test
    fun registerLoginMeAndProfile() = serverTest {
        val c = newApi()
        val reg = c.api.register("Ada@Example.com", "geheim1234", "Ada")
        assertEquals("ada@example.com", reg.user!!.email)
        assertEquals(UserRole.DRIVER, reg.user!!.role)
        assertTrue(reg.accessToken.isNotBlank() && reg.refreshToken.isNotBlank())

        // Passwort ist bcrypt-gehasht (wie Go), Refresh-Token nur als SHA-256 gespeichert
        transaction(TestDb.database) {
            val hash = Users.selectAll().single()[Users.passwordHash]!!
            assertTrue(hash.startsWith("$2a$"), hash)
            val stored = RefreshTokens.selectAll().single()[RefreshTokens.tokenHash]
            assertNotEquals(reg.refreshToken, stored)
            assertEquals(AuthService.hashToken(reg.refreshToken), stored)
        }

        val other = newApi()
        val login = other.api.login("ada@example.com", "geheim1234")
        assertEquals(reg.user!!.id, login.user!!.id)

        assertEquals("Ada", other.api.me().name)
        val upd = other.api.updateMe(UpdateUserRequest(name = "Ada L.", phone = "+49 421 1234"))
        assertEquals("Ada L.", upd.name)
        assertEquals("+49 421 1234", upd.phone)
        assertEquals("Ada L.", other.api.me().name)

        val host = other.api.updateRole(UserRole.HOST)
        assertEquals(UserRole.HOST, host.role)
        val e = assertFailsWith<ApiException> { other.api.updateRole(UserRole.ADMIN) }
        assertEquals(422, e.status)
    }

    @Test
    fun registerErrors() = serverTest {
        val api = newApi().api
        api.register("ada@example.com", "geheim1234", "Ada")
        val dup = assertFailsWith<ApiException> { newApi().api.register("ada@example.com", "geheim1234", "Ada 2") }
        assertEquals(409, dup.status)
        assertEquals(ErrorCodes.CONFLICT, dup.code)

        val bad = raw.postJson("/api/v1/auth/register", """{"email":"kaputt","password":"x","name":"Ada"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
        assertEquals(ErrorCodes.VALIDATION_ERROR, errOf(bad.text()).code)

        val json = raw.postJson("/api/v1/auth/login", "{nicht json")
        assertEquals(HttpStatusCode.BadRequest, json.status)
        assertEquals(ErrorCodes.BAD_REQUEST, errOf(json.text()).code)
    }

    @Test
    fun loginRejectsWrongCredentials() = serverTest {
        newApi().api.register("ada@example.com", "geheim1234", "Ada")
        for ((mail, pw) in listOf("ada@example.com" to "falsch1234", "unbekannt@example.com" to "geheim1234")) {
            val e = assertFailsWith<ApiException> { newApi().api.login(mail, pw) }
            assertEquals(401, e.status)
            assertEquals("invalid credentials", e.message)
        }
    }

    @Test
    fun protectedRoutesRequireValidJwt() = serverTest {
        val none = raw.getAuth("/api/v1/users/me", null)
        assertEquals(HttpStatusCode.Unauthorized, none.status)
        assertEquals(ErrorCodes.UNAUTHORIZED, errOf(none.text()).code)
        assertEquals(HttpStatusCode.Unauthorized, raw.getAuth("/api/v1/users/me", "abc.def.ghi").status)

        val foreign = TokenService(testConfig.copy(jwtSecret = "x".repeat(40))).createAccessToken("11111111-2222-3333-4444-555555555555", UserRole.HOST)
        assertEquals(HttpStatusCode.Unauthorized, raw.getAuth("/api/v1/users/me", foreign).status)

        // gueltiges Token eines nicht (mehr) existierenden Benutzers
        val ghost = TokenService(testConfig).createAccessToken("11111111-2222-3333-4444-555555555555", UserRole.DRIVER)
        assertEquals(HttpStatusCode.NotFound, raw.getAuth("/api/v1/users/me", ghost).status)

        val expired = TokenService(testConfig).createAccessToken("11111111-2222-3333-4444-555555555555", UserRole.DRIVER, Date(0))
        assertEquals(HttpStatusCode.Unauthorized, raw.getAuth("/api/v1/users/me", expired).status)
    }

    @Test
    fun refreshRotatesAndOldTokenIsRevoked() = serverTest {
        val c = newApi()
        val reg = c.api.register("ada@example.com", "geheim1234", "Ada")
        val pair = c.api.refresh(reg.refreshToken)
        assertNotEquals(reg.refreshToken, pair.refreshToken)
        // altes Refresh-Token ist verbraucht (Rotation)
        val reuse = assertFailsWith<ApiException> { newApi().api.refresh(reg.refreshToken) }
        assertEquals(401, reuse.status)
        // neues funktioniert; Access-Token des neuen Paares ist gueltig
        val pair2 = newApi().api.refresh(pair.refreshToken)
        assertEquals(200, raw.getAuth("/api/v1/users/me", pair2.accessToken).status.value)
        transaction(TestDb.database) { assertEquals(1L, RefreshTokens.selectAll().count()) }

        assertEquals(401, assertFailsWith<ApiException> { newApi().api.refresh("irgendwas") }.status)
    }

    @Test
    fun logoutRevokesRefreshToken() = serverTest {
        val c = newApi()
        val reg = c.api.register("ada@example.com", "geheim1234", "Ada")
        c.api.logout()
        assertNull(c.store.accessToken())
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.refresh(reg.refreshToken) }.status)
    }

    @Test
    fun expiredRefreshTokenIsRejected() = serverTest(config = testConfig.copy(refreshTokenTtlDays = -1)) {
        val reg = newApi().api.register("ada@example.com", "geheim1234", "Ada")
        val e = assertFailsWith<ApiException> { newApi().api.refresh(reg.refreshToken) }
        assertEquals(401, e.status)
        assertEquals("refresh token expired", e.message)
    }

    @Test
    fun deleteAccountRemovesUserAndTokens() = serverTest {
        val c = newApi()
        c.api.register("ada@example.com", "geheim1234", "Ada")
        c.api.deleteMe()
        transaction(TestDb.database) {
            assertEquals(0L, Users.selectAll().count())
            assertEquals(0L, RefreshTokens.selectAll().count())
        }
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.login("ada@example.com", "geheim1234") }.status)
    }

    @Test
    fun appleSignInIsNotConfiguredByDefault() = serverTest {
        val e = assertFailsWith<ApiException> { newApi().api.loginWithApple("x.y.z") }
        assertEquals(501, e.status)
        assertEquals(ErrorCodes.NOT_IMPLEMENTED, e.code)
    }

    @Test
    fun appleSignInWithLocallySignedToken() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pub = kp.public as RSAPublicKey
        val alg = Algorithm.RSA256(pub, kp.private as RSAPrivateKey)
        fun token(aud: String, sub: String = "apple-sub-1") = JWT.create().withKeyId("k1")
            .withIssuer("https://appleid.apple.com").withAudience(aud).withSubject(sub)
            .withClaim("email", "ada@privaterelay.appleid.com").withClaim("email_verified", "true")
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000)).sign(alg)

        val verifier = AppleJwksTokenVerifier("app.chargeshare") { kid -> pub.takeIf { kid == "k1" } }
        runBlocking {
            serverTest(apple = verifier) {
                val c = newApi()
                val r1 = c.api.loginWithApple(token("app.chargeshare"), "Ada Apple")
                assertEquals("Ada Apple", r1.user!!.name)
                // zweiter Login mit demselben sub -> gleicher Benutzer
                val r2 = newApi().api.loginWithApple(token("app.chargeshare"), null)
                assertEquals(r1.user!!.id, r2.user!!.id)
                // falsche Audience wird abgelehnt
                assertEquals(401, assertFailsWith<ApiException> { newApi().api.loginWithApple(token("evil.app")) }.status)
                // fremde Signatur wird abgelehnt
                val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                val forged = JWT.create().withKeyId("k1").withIssuer("https://appleid.apple.com").withAudience("app.chargeshare")
                    .withSubject("x").sign(Algorithm.RSA256(other.public as RSAPublicKey, other.private as RSAPrivateKey))
                assertEquals(401, assertFailsWith<ApiException> { newApi().api.loginWithApple(forged) }.status)
            }
        }
    }
}
