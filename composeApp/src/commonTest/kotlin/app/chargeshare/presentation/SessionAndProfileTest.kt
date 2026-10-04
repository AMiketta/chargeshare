package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.Outcome
import app.chargeshare.data.SessionState
import app.chargeshare.presentation.profile.ProfileField
import app.chargeshare.presentation.profile.ProfileViewModel
import app.chargeshare.presentation.session.SessionViewModel
import app.chargeshare.shared.model.ProfileLimits
import app.chargeshare.shared.model.UserRole
import app.chargeshare.shared.model.UserStats
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun statsAreLoadedWhenTheProfileOpens() {
        val stats = UserStats(sessions = 48, kwhCharged = 423.0, ratingAvg = 4.9, ratingCount = 12)
        repository.statsResult = Outcome.Success(stats)
        val profile = ProfileViewModel(repository)
        assertEquals(stats, profile.state.value.stats)
        assertEquals(1, repository.statsCalls)
    }

    @Test
    fun failedStatsStayHiddenWithoutAnErrorBanner() {
        repository.statsResult = failure(AppError.Network)
        val profile = ProfileViewModel(repository)
        assertNull(profile.state.value.stats)
        assertNull(profile.state.value.error)
    }

    @Test
    fun statsCanBeReloaded() {
        val profile = ProfileViewModel(repository)
        repository.statsResult = Outcome.Success(UserStats(sessions = 3))
        profile.loadStats()
        assertEquals(3, profile.state.value.stats?.sessions)
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

/** Profil bearbeiten: Name und Telefon mit Validierung. */
class ProfileEditTest {
    private val repository = FakeAuthRepository(initial = SessionState.SignedIn(testUser()))
    private lateinit var viewModel: ProfileViewModel

    @BeforeTest
    fun setUp() {
        TestMain.install()
        viewModel = ProfileViewModel(repository)
    }

    @AfterTest fun tearDown() = TestMain.uninstall()

    @Test
    fun editStartsWithCurrentValues() {
        viewModel.onEditRequested(testUser().copy(phone = "0421 1234567"))
        val edit = assertNotNull(viewModel.state.value.edit)
        assertEquals("Demo Fahrer", edit.name)
        assertEquals("0421 1234567", edit.phone)
    }

    @Test
    fun savingSendsTrimmedValuesAndUpdatesTheSession() {
        viewModel.onEditRequested(testUser())
        viewModel.onEditNameChanged("  Ada Lovelace ")
        viewModel.onEditPhoneChanged(" +49 421 1234567 ")
        viewModel.onEditSaved()
        assertEquals(listOf("Ada Lovelace" to "+49 421 1234567"), repository.profileUpdates)
        assertNull(viewModel.state.value.edit)
        val session = assertIs<SessionState.SignedIn>(repository.session.value)
        assertEquals("Ada Lovelace", session.user.name)
        assertEquals("+49 421 1234567", session.user.phone)
    }

    @Test
    fun emptyPhoneIsAllowedAndRemovesTheNumber() {
        viewModel.onEditRequested(testUser().copy(phone = "0421 1234567"))
        viewModel.onEditPhoneChanged("")
        viewModel.onEditSaved()
        assertEquals(listOf("Demo Fahrer" to ""), repository.profileUpdates)
        assertNull(assertIs<SessionState.SignedIn>(repository.session.value).user.phone)
    }

    @Test
    fun invalidInputIsRejectedWithoutCallingTheServer() {
        viewModel.onEditRequested(testUser())
        viewModel.onEditNameChanged("   ")
        viewModel.onEditPhoneChanged("abc")
        viewModel.onEditSaved()
        assertEquals(setOf(ProfileField.Name, ProfileField.Phone), assertNotNull(viewModel.state.value.edit).errors)
        assertTrue(repository.profileUpdates.isEmpty())
        // Korrektur räumt den jeweiligen Fehler wieder ab.
        viewModel.onEditNameChanged("Ada")
        assertEquals(setOf(ProfileField.Phone), assertNotNull(viewModel.state.value.edit).errors)
    }

    @Test
    fun tooLongNameIsRejected() {
        viewModel.onEditRequested(testUser())
        viewModel.onEditNameChanged("x".repeat(ProfileLimits.MAX_NAME_LENGTH + 1))
        viewModel.onEditSaved()
        assertTrue(ProfileField.Name in assertNotNull(viewModel.state.value.edit).errors)
    }

    @Test
    fun serverErrorKeepsTheFormOpen() {
        repository.updateProfileResult = failure(AppError.Server)
        viewModel.onEditRequested(testUser())
        viewModel.onEditNameChanged("Ada")
        viewModel.onEditSaved()
        val edit = assertNotNull(viewModel.state.value.edit)
        assertEquals(AppError.Server, edit.saveError)
        assertFalse(edit.isSaving)
        assertEquals("Ada", edit.name)
        assertEquals("Demo Fahrer", assertIs<SessionState.SignedIn>(repository.session.value).user.name)
    }

    @Test
    fun cancelDiscardsTheForm() {
        viewModel.onEditRequested(testUser())
        viewModel.onEditNameChanged("Ada")
        viewModel.onEditDismissed()
        assertNull(viewModel.state.value.edit)
        assertTrue(repository.profileUpdates.isEmpty())
    }
}
