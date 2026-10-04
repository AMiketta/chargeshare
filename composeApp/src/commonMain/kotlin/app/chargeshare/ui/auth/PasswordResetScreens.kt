package app.chargeshare.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.chargeshare.presentation.auth.ForgotPasswordUiState
import app.chargeshare.presentation.auth.ForgotPasswordViewModel
import app.chargeshare.presentation.auth.ResetPasswordUiState
import app.chargeshare.presentation.auth.ResetPasswordViewModel
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.FormTextField
import app.chargeshare.ui.components.HeroHeader
import app.chargeshare.ui.components.PrimaryButton
import app.chargeshare.ui.components.SuccessText

/** Verbindet [ForgotPasswordViewModel] mit [ForgotPasswordScreen]. */
@Composable
fun ForgotPasswordRoute(
    viewModel: ForgotPasswordViewModel,
    onBack: () -> Unit,
    onHaveToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    ForgotPasswordScreen(state, viewModel::onEmailChange, viewModel::onSubmit, onBack, onHaveToken, modifier)
}

/** „Passwort vergessen“: E-Mail eingeben, danach neutrale Bestätigung. */
@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onHaveToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroHeader(title = Strings.AppName, subtitle = Strings.Tagline)
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = Strings.ForgotPasswordTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = ChargeShareColors.PrimaryText,
            )
            Text(Strings.ForgotPasswordIntro, style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.OnSurfaceVariant)
            FormTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = Strings.FieldEmail,
                keyboardType = KeyboardType.Email,
                errorText = state.emailError?.let(Strings::fieldError),
                enabled = !state.isSubmitting,
            )
            state.submitError?.let { ErrorText(Strings.errorMessage(it)) }
            if (state.sent) SuccessText(Strings.ForgotPasswordSent)
            PrimaryButton(text = Strings.ForgotPasswordSend, onClick = onSubmit, isLoading = state.isSubmitting)
            TextButton(onClick = onHaveToken, enabled = !state.isSubmitting) { Text(Strings.ForgotPasswordHaveToken) }
            TextButton(onClick = onBack, enabled = !state.isSubmitting) { Text(Strings.BackToLogin) }
        }
    }
}

/** Verbindet [ResetPasswordViewModel] mit [ResetPasswordScreen]; nach Erfolg geht es zurück zur Anmeldung. */
@Composable
fun ResetPasswordRoute(
    viewModel: ResetPasswordViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ResetPasswordScreen(state, viewModel::onTokenChange, viewModel::onPasswordChange, viewModel::onSubmit, onBack, modifier)
}

/** Neues Passwort festlegen; das Token ist meist schon aus dem Mail-Link vorbelegt. */
@Composable
fun ResetPasswordScreen(
    state: ResetPasswordUiState,
    onTokenChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroHeader(title = Strings.AppName, subtitle = Strings.Tagline)
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = Strings.ResetPasswordTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = ChargeShareColors.PrimaryText,
            )
            Text(Strings.ResetPasswordIntro, style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.OnSurfaceVariant)
            FormTextField(
                value = state.token,
                onValueChange = onTokenChange,
                label = Strings.ResetPasswordToken,
                errorText = state.tokenError?.let(Strings::fieldError),
                enabled = !state.isSubmitting,
            )
            FormTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = Strings.ResetPasswordNewPassword,
                isPassword = true,
                errorText = state.passwordError?.let(Strings::fieldError),
                supportingText = Strings.PasswordHint,
                enabled = !state.isSubmitting,
            )
            state.submitError?.let { ErrorText(Strings.resetPasswordErrorMessage(it)) }
            PrimaryButton(text = Strings.ResetPasswordSave, onClick = onSubmit, isLoading = state.isSubmitting)
            TextButton(onClick = onBack, enabled = !state.isSubmitting) { Text(Strings.BackToLogin) }
        }
    }
}
