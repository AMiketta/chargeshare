package app.chargeshare.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.chargeshare.presentation.auth.AuthMode
import app.chargeshare.presentation.auth.AuthUiState
import app.chargeshare.presentation.auth.AuthViewModel
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.FormTextField
import app.chargeshare.ui.components.HeroHeader
import app.chargeshare.ui.components.PrimaryButton
import app.chargeshare.ui.components.SuccessText

/** Verbindet [AuthViewModel] mit dem zustandslosen [AuthScreen]. */
@Composable
fun AuthRoute(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    notice: String? = null,
    onForgotPassword: () -> Unit = {},
    demoAccounts: List<DemoAccount> = emptyList(),
) {
    val state by viewModel.state.collectAsState()
    AuthScreen(
        state = state,
        onModeChange = viewModel::onModeChange,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
        notice = notice,
        onForgotPassword = onForgotPassword,
        demoAccounts = demoAccounts,
        onQuickLogin = { viewModel.onQuickLogin(it.email, it.password) },
    )
}

/**
 * Login- und Registrierungsmaske.
 * @param demoAccounts nur in Debug-Builds befüllt: Schnellanmeldung mit den Demo-Konten des lokalen Backends
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    onForgotPassword: () -> Unit = {},
    demoAccounts: List<DemoAccount> = emptyList(),
    onQuickLogin: (DemoAccount) -> Unit = {},
) {
    val isRegister = state.mode == AuthMode.Register
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroHeader(title = Strings.AppName, subtitle = Strings.Tagline)
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = if (isRegister) Strings.CreateAccount else Strings.Login,
                style = MaterialTheme.typography.headlineSmall,
                color = ChargeShareColors.PrimaryText,
            )
            notice?.let { SuccessText(it) }
            if (isRegister) {
                FormTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = Strings.FieldName,
                    errorText = state.nameError?.let(Strings::fieldError),
                    enabled = !state.isSubmitting,
                )
            }
            FormTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = Strings.FieldEmail,
                keyboardType = KeyboardType.Email,
                errorText = state.emailError?.let(Strings::fieldError),
                enabled = !state.isSubmitting,
            )
            FormTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = Strings.FieldPassword,
                isPassword = true,
                errorText = state.passwordError?.let(Strings::fieldError),
                supportingText = if (isRegister) Strings.PasswordHint else null,
                enabled = !state.isSubmitting,
            )
            state.submitError?.let { error ->
                ErrorText(if (isRegister) Strings.registerErrorMessage(error) else Strings.loginErrorMessage(error))
            }
            PrimaryButton(
                text = if (isRegister) Strings.Register else Strings.Login,
                onClick = onSubmit,
                isLoading = state.isSubmitting,
            )
            if (!isRegister) {
                TextButton(onClick = onForgotPassword, enabled = !state.isSubmitting) { Text(Strings.ForgotPasswordLink) }
            }
            TextButton(
                onClick = { onModeChange(if (isRegister) AuthMode.Login else AuthMode.Register) },
                enabled = !state.isSubmitting,
            ) {
                Text(if (isRegister) Strings.HaveAccount else Strings.NoAccountYet)
            }
            if (!isRegister && demoAccounts.isNotEmpty()) {
                DebugQuickLogin(demoAccounts, onQuickLogin, enabled = !state.isSubmitting)
            }
        }
    }
}

/** Dezente Debug-Leiste: ein umrandeter Button je Demo-Konto. */
@Composable
private fun DebugQuickLogin(accounts: List<DemoAccount>, onQuickLogin: (DemoAccount) -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(Strings.DebugQuickLogin, style = MaterialTheme.typography.labelMedium, color = ChargeShareColors.OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            accounts.forEach { account ->
                OutlinedButton(
                    onClick = { onQuickLogin(account) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = Spacing.MinTouchTarget),
                ) { Text(account.label) }
            }
        }
    }
}
