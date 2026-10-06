package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.presentation.auth.AuthMode
import app.chargeshare.presentation.auth.AuthViewModel
import app.chargeshare.presentation.auth.FieldError
import app.chargeshare.presentation.auth.validateEmail
import app.chargeshare.presentation.auth.validatePassword
import app.chargeshare.testing.FakeAuthRepository
import app.chargeshare.ui.auth.DemoAccounts
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthViewModelTest {
    private val repository = FakeAuthRepository()

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    @Test
    fun initialStateIsLoginWithoutErrors() {
        val state = AuthViewModel(repository).state.value
        assertEquals(AuthMode.Login, state.mode)
        assertNull(state.emailError)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun loginWithEmptyFieldsShowsRequiredErrorsAndDoesNotCallServer() {
        val viewModel = AuthViewModel(repository)
        viewModel.onSubmit()
        val state = viewModel.state.value
        assertEquals(FieldError.Required, state.emailError)
        assertEquals(FieldError.Required, state.passwordError)
        assertTrue(repository.loginCalls.isEmpty())
    }

    @Test
    fun loginWithInvalidEmailShowsInvalidEmailError() {
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChange("kein-email")
        viewModel.onPasswordChange("geheim")
        viewModel.onSubmit()
        assertEquals(FieldError.InvalidEmail, viewModel.state.value.emailError)
    }

    @Test
    fun successfulLoginCallsRepositoryAndClearsPassword() {
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChange("demo@chargeshare.local")
        viewModel.onPasswordChange("demo1234")
        viewModel.onSubmit()
        assertEquals(listOf("demo@chargeshare.local" to "demo1234"), repository.loginCalls)
        assertEquals("", viewModel.state.value.password)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun wrongPasswordShowsUnauthorizedSubmitError() {
        repository.loginResult = failure(AppError.Unauthorized)
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChange("demo@chargeshare.local")
        viewModel.onPasswordChange("falsch")
        viewModel.onSubmit()
        assertEquals(AppError.Unauthorized, viewModel.state.value.submitError)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun networkErrorIsReportedAndPasswordIsKept() {
        repository.loginResult = failure(AppError.Network)
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChange("demo@chargeshare.local")
        viewModel.onPasswordChange("demo1234")
        viewModel.onSubmit()
        assertEquals(AppError.Network, viewModel.state.value.submitError)
        assertEquals("demo1234", viewModel.state.value.password)
    }

    @Test
    fun registerRequiresNameAndEightCharacterPassword() {
        val viewModel = AuthViewModel(repository)
        viewModel.onModeChange(AuthMode.Register)
        viewModel.onEmailChange("neu@chargeshare.local")
        viewModel.onPasswordChange("kurz")
        viewModel.onSubmit()
        val state = viewModel.state.value
        assertEquals(FieldError.Required, state.nameError)
        assertEquals(FieldError.PasswordTooShort, state.passwordError)
        assertTrue(repository.registerCalls.isEmpty())
    }

    @Test
    fun validRegistrationCallsRegister() {
        val viewModel = AuthViewModel(repository)
        viewModel.onModeChange(AuthMode.Register)
        viewModel.onNameChange("Neu Nutzer")
        viewModel.onEmailChange("neu@chargeshare.local")
        viewModel.onPasswordChange("langgenug1")
        viewModel.onSubmit()
        assertEquals(listOf(Triple("Neu Nutzer", "neu@chargeshare.local", "langgenug1")), repository.registerCalls)
    }

    @Test
    fun editingAFieldClearsItsError() {
        val viewModel = AuthViewModel(repository)
        viewModel.onSubmit()
        viewModel.onEmailChange("a@b.de")
        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun switchingModeKeepsEmailButResetsOtherFields() {
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChange("a@b.de")
        viewModel.onPasswordChange("x")
        viewModel.onModeChange(AuthMode.Register)
        assertEquals("a@b.de", viewModel.state.value.email)
        assertEquals("", viewModel.state.value.password)
    }

    @Test
    fun quickLoginSignsInWithDemoAccountEvenFromRegisterMode() {
        val viewModel = AuthViewModel(repository)
        viewModel.onModeChange(AuthMode.Register)
        viewModel.onEmailChange("andere@chargeshare.local")
        viewModel.onQuickLogin(DemoAccounts.Host.email, DemoAccounts.Host.password)
        assertEquals(listOf("demo-host@chargeshare.local" to "demo1234"), repository.loginCalls)
        assertTrue(repository.registerCalls.isEmpty())
        assertEquals(AuthMode.Login, viewModel.state.value.mode)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun validationRulesMatchBackend() {
        assertNull(validateEmail("a@b.de"))
        assertEquals(FieldError.InvalidEmail, validateEmail("a@b"))
        assertNull(validatePassword("x", enforceMinLength = false))
        assertEquals(FieldError.PasswordTooShort, validatePassword("1234567", enforceMinLength = true))
        assertNull(validatePassword("12345678", enforceMinLength = true))
    }
}
