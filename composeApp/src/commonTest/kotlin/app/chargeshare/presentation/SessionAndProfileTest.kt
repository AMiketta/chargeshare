package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.SessionState
import app.chargeshare.presentation.profile.ProfileViewModel
import app.chargeshare.presentation.session.SessionViewModel
import app.chargeshare.shared.model.UserRole
import app.chargeshare.testing.FakeAuthRepository
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.testUser
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionAndProfileTest {
    private val repository = FakeAuthRepository(initial = SessionState.Restoring)

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    @Test
    fun sessionRestoresStoredLoginOnStart() {
        repository.restoredState = SessionState.SignedIn(testUser())
        val viewModel = SessionViewModel(repository)
        assertIs<SessionState.SignedIn>(viewModel.session.value)
    }

    @Test
    fun sessionIsSignedOutWithoutStoredLogin() {
        val viewModel = SessionViewModel(repository)
        assertEquals(SessionState.SignedOut, viewModel.session.value)
    }

    @Test
    fun logoutCallsRepositoryAndSignsOut() {
        val profile = ProfileViewModel(repository)
        profile.onLogout()
        assertEquals(1, repository.logoutCalls)
        assertEquals(SessionState.SignedOut, repository.session.value)
        assertFalse(profile.state.value.isBusy)
    }

    @Test
    fun becomeHostUpdatesRoleInSession() {
        val profile = ProfileViewModel(repository)
        profile.onBecomeHost()
        val session = repository.session.value
        assertIs<SessionState.SignedIn>(session)
        assertEquals(UserRole.HOST, session.user.role)
    }

    @Test
    fun becomeHostFailureIsShown() {
        repository.becomeHostResult = failure(AppError.Network)
        val profile = ProfileViewModel(repository)
        profile.onBecomeHost()
        assertEquals(AppError.Network, profile.state.value.error)
    }

    @Test
    fun deleteAccountNeedsConfirmation() {
        val profile = ProfileViewModel(repository)
        profile.onDeleteRequested()
        assertTrue(profile.state.value.showDeleteConfirmation)
        profile.onDeleteDismissed()
        assertFalse(profile.state.value.showDeleteConfirmation)
        assertEquals(SessionState.Restoring, repository.session.value)
        profile.onDeleteRequested()
        profile.onDeleteConfirmed()
        assertEquals(SessionState.SignedOut, repository.session.value)
    }
}
