package app.chargeshare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chargeshare.data.DeepLink
import app.chargeshare.di.AppContainer
import app.chargeshare.presentation.auth.AuthViewModel
import app.chargeshare.presentation.auth.ForgotPasswordViewModel
import app.chargeshare.presentation.auth.ResetPasswordViewModel
import app.chargeshare.ui.auth.AuthRoute
import app.chargeshare.ui.auth.ForgotPasswordRoute
import app.chargeshare.ui.auth.ResetPasswordRoute

/** Schritte vor der Anmeldung. */
internal sealed interface SignedOutStep {
    data class Login(val notice: String? = null) : SignedOutStep
    data object Forgot : SignedOutStep
    data class Reset(val token: String) : SignedOutStep
}

/**
 * Alles, was man ohne Konto sieht: Anmeldung, „Passwort vergessen“ und „Neues Passwort“.
 * Ein Deep Link aus der Reset-Mail (`chargeshare://reset-password?token=…`) öffnet direkt den letzten Schritt.
 */
@Composable
internal fun SignedOutContent(container: AppContainer) {
    var step by remember { mutableStateOf<SignedOutStep>(SignedOutStep.Login()) }
    val link by container.deepLinks.pending.collectAsState()
    LaunchedEffect(link) {
        val current = link as? DeepLink.ResetPassword ?: return@LaunchedEffect
        step = SignedOutStep.Reset(current.token)
        container.deepLinks.consume(current)
    }
    // Jeder Schritt hat einen eigenen ViewModel-Speicher: Eingaben und „gesendet“-Hinweis starten frisch.
    key(step) {
        val owner = rememberScopedViewModelStoreOwner()
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            when (val current = step) {
                is SignedOutStep.Login -> {
                    val viewModel = viewModel { AuthViewModel(container.authRepository) }
                    AuthRoute(viewModel, notice = current.notice, onForgotPassword = { step = SignedOutStep.Forgot })
                }
                SignedOutStep.Forgot -> {
                    val viewModel = viewModel { ForgotPasswordViewModel(container.authRepository) }
                    ForgotPasswordRoute(
                        viewModel,
                        onBack = { step = SignedOutStep.Login() },
                        onHaveToken = { step = SignedOutStep.Reset(token = "") },
                    )
                }
                is SignedOutStep.Reset -> {
                    val viewModel = viewModel { ResetPasswordViewModel(container.authRepository, current.token) }
                    ResetPasswordRoute(
                        viewModel,
                        onDone = { step = SignedOutStep.Login(notice = Strings.ResetPasswordDone) },
                        onBack = { step = SignedOutStep.Login() },
                    )
                }
            }
        }
    }
}
