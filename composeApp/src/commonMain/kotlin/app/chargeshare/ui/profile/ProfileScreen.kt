package app.chargeshare.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.chargeshare.data.isHost
import app.chargeshare.presentation.profile.ProfileUiState
import app.chargeshare.presentation.profile.ProfileViewModel
import app.chargeshare.shared.model.User
import app.chargeshare.shared.model.UserRole
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AmberButton
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.HeroHeader
import app.chargeshare.ui.components.InfoPill
import app.chargeshare.ui.components.SurfaceCard
import app.chargeshare.ui.initialsOf

@Composable
fun ProfileRoute(
    user: User,
    viewModel: ProfileViewModel,
    apiBaseUrl: String,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    ProfileScreen(
        user = user,
        state = state,
        apiBaseUrl = apiBaseUrl,
        onLogout = viewModel::onLogout,
        onBecomeHost = viewModel::onBecomeHost,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        modifier = modifier,
    )
}

/** Profil mit Rolle, Host-Einstieg, Abmelden und Konto löschen. */
@Composable
fun ProfileScreen(
    user: User,
    state: ProfileUiState,
    apiBaseUrl: String,
    onLogout: () -> Unit,
    onBecomeHost: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        HeroHeader(title = Strings.ProfileTitle) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                Avatar(user.name)
                Column {
                    Text(user.name, style = MaterialTheme.typography.titleLarge, color = ChargeShareColors.OnPrimary)
                    Text(user.email, style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.OnPrimary)
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                InfoPill(roleLabel(user.role))
                InfoPill(
                    text = if (user.isVerified) Strings.Verified else Strings.NotVerified,
                    containerColor = ChargeShareColors.SurfaceVariant,
                    contentColor = ChargeShareColors.OnSurfaceVariant,
                )
            }
            state.error?.let { ErrorText(Strings.errorMessage(it)) }
            if (!user.isHost) {
                SurfaceCard {
                    Text(Strings.BecomeHostTitle, style = MaterialTheme.typography.titleMedium)
                    Text(Strings.BecomeHostBody, style = MaterialTheme.typography.bodyMedium)
                    AmberButton(Strings.BecomeHost, onBecomeHost, isLoading = state.isBusy)
                }
            }
            OutlinedButton(onClick = onLogout, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.MinTouchTarget)) { Text(Strings.Logout) }
            TextButton(onClick = onDeleteRequested, enabled = !state.isBusy) {
                Text(Strings.DeleteAccount, color = MaterialTheme.colorScheme.error)
            }
            Text(Strings.PrivacyNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("API: $apiBaseUrl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDeleteDismissed,
            title = { Text(Strings.DeleteAccountTitle) },
            text = { Text(Strings.DeleteAccountBody) },
            confirmButton = { TextButton(onClick = onDeleteConfirmed) { Text(Strings.DeleteAccountConfirm, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDeleteDismissed) { Text(Strings.Cancel) } },
        )
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape).background(ChargeShareColors.Accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(initialsOf(name), style = MaterialTheme.typography.titleMedium, color = ChargeShareColors.OnAccent)
    }
}

private val AVATAR_SIZE = 56.dp

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.DRIVER -> Strings.RoleDriver
    UserRole.HOST -> Strings.RoleHost
    UserRole.BOTH -> Strings.RoleBoth
    UserRole.ADMIN -> Strings.RoleAdmin
}
