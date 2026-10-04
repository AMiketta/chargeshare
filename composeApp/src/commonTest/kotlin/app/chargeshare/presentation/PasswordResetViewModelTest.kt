package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.DeepLink
import app.chargeshare.presentation.auth.FieldError
import app.chargeshare.presentation.auth.ForgotPasswordViewModel
import app.chargeshare.presentation.auth.ResetPasswordViewModel
import app.chargeshare.testing.FakeAuthRepository
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** „Passwort vergessen“ und „Neues Passwort“ sowie der Deep Link aus der Mail. */
class PasswordResetViewModelTest {
    private val repository = FakeAuthRepository()

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    @Test
    fun forgotPasswordRejectsInvalidEmailWithoutCallingTheServer() {
        val viewModel = ForgotPasswordViewModel(repository)
        viewModel.onEmailChange("kein-email")
        viewModel.onSubmit()
        assertEquals(FieldError.InvalidEmail, viewModel.state.value.emailError)
        assertTrue(repository.forgotPasswordCalls.isEmpty())
    }

    @Test
    fun forgotPasswordShowsNeutralConfirmationAfterSuccess() {
        val viewModel = ForgotPasswordViewModel(repository)
        viewModel.onEmailChange("a@b.de")
        viewModel.onSubmit()
        val state = viewModel.state.value
        assertTrue(state.sent)
        assertFalse(state.isSubmitting)
        assertEquals(listOf("a@b.de"), repository.forgotPasswordCalls)
    }

    @Test
    fun forgotPasswordShowsNetworkErrorAndStaysUnsent() {
        repository.forgotPasswordResult = failure(AppError.Network)
        val viewModel = ForgotPasswordViewModel(repository)
        viewModel.onEmailChange("a@b.de")
        viewModel.onSubmit()
        assertEquals(AppError.Network, viewModel.state.value.submitError)
        assertFalse(viewModel.state.value.sent)
    }

    @Test
    fun resetPasswordUsesTokenFromTheLinkAndEnforcesMinimumLength() {
        val viewModel = ResetPasswordViewModel(repository, initialToken = "tok-123")
        assertEquals("tok-123", viewModel.state.value.token)
        viewModel.onPasswordChange("kurz")
        viewModel.onSubmit()
        assertEquals(FieldError.PasswordTooShort, viewModel.state.value.passwordError)
        assertTrue(repository.resetPasswordCalls.isEmpty())
    }

    @Test
    fun resetPasswordRequiresAToken() {
        val viewModel = ResetPasswordViewModel(repository)
        viewModel.onPasswordChange("langgenug1")
        viewModel.onSubmit()
        assertEquals(FieldError.Required, viewModel.state.value.tokenError)
    }

    @Test
    fun resetPasswordSuccessClearsThePasswordAndMarksDone() {
        val viewModel = ResetPasswordViewModel(repository, initialToken = "tok-123")
        viewModel.onPasswordChange("langgenug1")
        viewModel.onSubmit()
        val state = viewModel.state.value
        assertTrue(state.done)
        assertEquals("", state.password)
        assertEquals(listOf("tok-123" to "langgenug1"), repository.resetPasswordCalls)
    }

    @Test
    fun resetPasswordKeepsTheFormOnAnInvalidToken() {
        repository.resetPasswordResult = failure(AppError.Validation(null))
        val viewModel = ResetPasswordViewModel(repository, initialToken = "alt")
        viewModel.onPasswordChange("langgenug1")
        viewModel.onSubmit()
        val state = viewModel.state.value
        assertFalse(state.done)
        assertTrue(state.submitError is AppError.Validation)
    }

    @Test
    fun resetPasswordDeepLinkCarriesTheToken() {
        assertEquals(DeepLink.ResetPassword("abc_DEF-123"), DeepLink.parse("chargeshare://reset-password?token=abc_DEF-123"))
        assertEquals(DeepLink.ResetPassword("x1"), DeepLink.parse("chargeshare://reset-password?foo=1&token=x1"))
        assertNull(DeepLink.parse("chargeshare://reset-password"))
        assertNull(DeepLink.parse("chargeshare://reset-password?token="))
    }
}
