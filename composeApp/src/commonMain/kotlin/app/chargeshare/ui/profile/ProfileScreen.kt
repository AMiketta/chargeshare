package app.chargeshare.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.chargeshare.data.isHost
import app.chargeshare.presentation.profile.ProfileEditState
import app.chargeshare.presentation.profile.ProfileField
import app.chargeshare.presentation.profile.ProfileUiState
import app.chargeshare.presentation.profile.ProfileViewModel
import app.chargeshare.presentation.theme.ThemeMode
import app.chargeshare.shared.model.User
import app.chargeshare.shared.model.UserRole
import app.chargeshare.shared.model.UserStats
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.ChoiceChips
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.FormTextField
import app.chargeshare.ui.components.InfoPill
import app.chargeshare.ui.components.MenuDivider
import app.chargeshare.ui.components.MenuGroup
import app.chargeshare.ui.components.MenuIconStyle
import app.chargeshare.ui.components.MenuItem
import app.chargeshare.ui.formatDecimal
import app.chargeshare.ui.formatKwhShort
import app.chargeshare.ui.initialsOf

@Composable
fun ProfileRoute(
    user: User,
    viewModel: ProfileViewModel,
    onOpenHost: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    debugInfo: String? = null,
) {
    val state by viewModel.state.collectAsState()
    ProfileScreen(
        user = user,
        state = state,
        debugInfo = debugInfo,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onLogout = viewModel::onLogout,
        onBecomeHost = viewModel::onBecomeHost,
        onOpenHost = onOpenHost,
        onEditRequested = { viewModel.onEditRequested(user) },
        onEditNameChanged = viewModel::onEditNameChanged,
        onEditPhoneChanged = viewModel::onEditPhoneChanged,
        onEditSaved = viewModel::onEditSaved,
        onEditDismissed = viewModel::onEditDismissed,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        modifier = modifier,
    )
}

/**
 * Profil im Design-Stil: grüner Kopf mit Avatar, Verifizierung und drei Kennzahlen, darunter gruppierte Menülisten.
 *
 * @param debugInfo Entwicklerzeile (z. B. API-Adresse); wird nur in Debug-Builds übergeben, sonst `null`
 * @param themeMode gewählte Darstellung (Hell/Dunkel/System); [onThemeModeChange] speichert eine neue Wahl
 */
@Composable
fun ProfileScreen(
    user: User,
    state: ProfileUiState,
    debugInfo: String?,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLogout: () -> Unit,
    onBecomeHost: () -> Unit,
    onOpenHost: () -> Unit,
    onEditRequested: () -> Unit,
    onEditNameChanged: (String) -> Unit,
    onEditPhoneChanged: (String) -> Unit,
    onEditSaved: () -> Unit,
    onEditDismissed: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Rein visueller Zustand: ob der Datenschutz-Hinweis offen ist.
    var showPrivacy by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        ProfileHeader(user, state.stats)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            state.error?.let { ErrorText(Strings.errorMessage(it)) }
            MenuGroup(label = Strings.ProfileHostGroup) {
                if (user.isHost) {
                    MenuItem(AppIcons.Bolt, Strings.ProfileHostOpen, onOpenHost, subtitle = Strings.ProfileHostOpenSubtitle, style = MenuIconStyle.Green)
                } else {
                    MenuItem(
                        AppIcons.Bolt,
                        Strings.BecomeHost,
                        onBecomeHost,
                        subtitle = Strings.ProfileBecomeHostSubtitle,
                        style = MenuIconStyle.Amber,
                        enabled = !state.isBusy,
                    )
                }
            }
            MenuGroup(label = Strings.ProfileAccountGroup) {
                MenuItem(AppIcons.Edit, Strings.EditProfile, onEditRequested, enabled = !state.isBusy)
                MenuDivider()
                MenuItem(AppIcons.Shield, Strings.ProfilePrivacy, { showPrivacy = true })
            }
            MenuGroup(label = Strings.ProfileAppearanceGroup) {
                ThemeModeSelector(themeMode, onThemeModeChange)
            }
            MenuGroup {
                MenuItem(AppIcons.Logout, Strings.Logout, onLogout, enabled = !state.isBusy)
                MenuDivider()
                MenuItem(
                    AppIcons.Delete,
                    Strings.DeleteAccount,
                    onDeleteRequested,
                    subtitle = Strings.ProfileDeleteSubtitle,
                    style = MenuIconStyle.Danger,
                    titleColor = ChargeShareColors.Error,
                    enabled = !state.isBusy,
                )
            }
            debugInfo?.let { Text("API: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    state.edit?.let { edit -> EditProfileDialog(edit, onEditNameChanged, onEditPhoneChanged, onEditSaved, onEditDismissed) }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(Strings.ProfilePrivacy) },
            text = { Text("${Strings.PrivacyNote}\n\n${Strings.ProfilePrivacyMore}") },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text(Strings.Close) } },
        )
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

/** Auswahl Hell / Dunkel / System als Chip-Reihe innerhalb einer Menügruppe. */
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    Column(
        modifier = Modifier.padding(vertical = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(Strings.ThemeLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Spacing.Medium))
        ChoiceChips(
            options = ThemeMode.entries,
            selected = selected,
            label = Strings::themeMode,
            onSelected = onSelected,
        )
    }
}

/** Tiefgrüner Kopf: Avatar, Name, E-Mail, Rolle/Verifizierung und (sobald geladen) die drei Kennzahlen. */
@Composable
private fun ProfileHeader(user: User, stats: UserStats?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChargeShareColors.Primary)
            .statusBarsPadding()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Avatar(user.name)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                Text(user.name, style = MaterialTheme.typography.titleLarge, color = ChargeShareColors.OnPrimary)
                Text(user.email, style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.OnPrimary.copy(alpha = HEADER_SECONDARY_ALPHA))
                user.phone?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.OnPrimary.copy(alpha = HEADER_SECONDARY_ALPHA))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    InfoPill(roleLabel(user.role), containerColor = ChargeShareColors.PrimaryLight, contentColor = ChargeShareColors.PrimaryText)
                    InfoPill(
                        text = if (user.isVerified) Strings.Verified else Strings.NotVerified,
                        containerColor = if (user.isVerified) ChargeShareColors.PrimarySurface else ChargeShareColors.SurfaceVariant,
                        contentColor = if (user.isVerified) ChargeShareColors.PrimaryText else ChargeShareColors.OnSurfaceVariant,
                    )
                }
            }
        }
        if (stats != null) StatRow(stats)
    }
}

@Composable
private fun StatRow(stats: UserStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        StatChip(stats.sessions.toString(), Strings.StatSessions, Modifier.weight(1f))
        StatChip(stats.ratingAvg?.let { formatDecimal(it, 1) } ?: Strings.StatNoValue, Strings.StatRating, Modifier.weight(1f))
        StatChip(formatKwhShort(stats.kwhCharged), Strings.StatKwh, Modifier.weight(1f))
    }
}

/** Kennzahl als weiße Karte: grüner Wert, grauer Text darunter. */
@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = ChargeShareColors.Surface) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.Small + Spacing.XSmall, horizontal = Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = ChargeShareColors.PrimaryText, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ChargeShareColors.OnSurfaceMuted, maxLines = 1)
        }
    }
}

/** Dialog zum Bearbeiten von Name und Telefonnummer. */
@Composable
private fun EditProfileDialog(
    edit: ProfileEditState,
    onNameChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.EditProfileTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                FormTextField(
                    value = edit.name,
                    onValueChange = onNameChanged,
                    label = Strings.FieldProfileName,
                    errorText = Strings.ProfileNameError.takeIf { ProfileField.Name in edit.errors },
                    enabled = !edit.isSaving,
                )
                FormTextField(
                    value = edit.phone,
                    onValueChange = onPhoneChanged,
                    label = Strings.FieldProfilePhone,
                    errorText = Strings.ProfilePhoneError.takeIf { ProfileField.Phone in edit.errors },
                    supportingText = Strings.ProfilePhoneHint,
                    keyboardType = KeyboardType.Phone,
                    enabled = !edit.isSaving,
                )
                edit.saveError?.let { ErrorText(Strings.errorMessage(it)) }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = !edit.isSaving) { Text(Strings.Save) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !edit.isSaving) { Text(Strings.Cancel) } },
    )
}

@Composable
private fun Avatar(name: String) {
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .clip(CircleShape)
            .background(ChargeShareColors.OnPrimary.copy(alpha = AVATAR_ALPHA))
            .border(AVATAR_BORDER, ChargeShareColors.OnPrimary.copy(alpha = AVATAR_BORDER_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initialsOf(name), style = MaterialTheme.typography.headlineSmall, color = ChargeShareColors.OnPrimary)
    }
}

private val AVATAR_SIZE = 58.dp
private val AVATAR_BORDER = 2.dp
private const val AVATAR_ALPHA = 0.18f
private const val AVATAR_BORDER_ALPHA = 0.5f
private const val HEADER_SECONDARY_ALPHA = 0.85f

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.DRIVER -> Strings.RoleDriver
    UserRole.HOST -> Strings.RoleHost
    UserRole.BOTH -> Strings.RoleBoth
    UserRole.ADMIN -> Strings.RoleAdmin
}
