package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.api.TokenStore
import app.chargeshare.shared.model.User
import app.chargeshare.shared.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Anmeldezustand der App. */
sealed interface SessionState {
    /** Gespeicherte Anmeldung wird beim Start geprüft. */
    data object Restoring : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

/** Anmeldung, Registrierung und Kontoverwaltung. Hält den aktuellen [SessionState]. */
interface AuthRepository {
    val session: StateFlow<SessionState>

    /** Prüft gespeicherte Tokens beim App-Start und setzt [session] entsprechend. */
    suspend fun restoreSession()
    suspend fun login(email: String, password: String): Outcome<User>
    suspend fun register(name: String, email: String, password: String): Outcome<User>
    suspend fun logout()

    /** Setzt die Rolle auf `host`, damit Wallboxen angelegt werden können. */
    suspend fun becomeHost(): Outcome<User>
    suspend fun deleteAccount(): Outcome<Unit>
}

/** [AuthRepository] über den gemeinsamen [ChargeShareApi]-Client. */
class ApiAuthRepository(
    private val api: ChargeShareApi,
    private val tokenStore: TokenStore,
) : AuthRepository {

    private val state = MutableStateFlow<SessionState>(SessionState.Restoring)
    override val session: StateFlow<SessionState> = state.asStateFlow()

    override suspend fun restoreSession() {
        if (tokenStore.refreshToken() == null) {
            state.value = SessionState.SignedOut
            return
        }
        state.value = when (val result = runCatchingOutcome { api.me() }) {
            is Outcome.Success -> SessionState.SignedIn(result.value)
            // Bei Netzfehlern bleiben die Tokens erhalten; der Nutzer sieht den Login und kann es erneut versuchen.
            is Outcome.Failure -> SessionState.SignedOut
        }
    }

    override suspend fun login(email: String, password: String): Outcome<User> =
        signIn { api.login(email.trim(), password).user }

    override suspend fun register(name: String, email: String, password: String): Outcome<User> =
        signIn { api.register(email.trim(), password, name.trim()).user }

    /** Der Server liefert den Nutzer bei Login/Registrierung mit; fehlt er, wird er nachgeladen. */
    private suspend fun signIn(call: suspend () -> User?): Outcome<User> {
        val result = runCatchingOutcome { call() ?: api.me() }
        if (result is Outcome.Success) state.value = SessionState.SignedIn(result.value)
        return result
    }

    override suspend fun logout() {
        runCatchingOutcome { api.logout() }
        tokenStore.clear()
        state.value = SessionState.SignedOut
    }

    override suspend fun becomeHost(): Outcome<User> {
        val result = runCatchingOutcome { api.updateRole(UserRole.HOST) }
        if (result is Outcome.Success) state.value = SessionState.SignedIn(result.value)
        return result
    }

    override suspend fun deleteAccount(): Outcome<Unit> {
        val result = runCatchingOutcome { api.deleteMe() }
        if (result is Outcome.Success) state.value = SessionState.SignedOut
        return result
    }
}

/** Darf der Nutzer Wallboxen anbieten? */
val User.isHost: Boolean
    get() = role == UserRole.HOST || role == UserRole.BOTH || role == UserRole.ADMIN
