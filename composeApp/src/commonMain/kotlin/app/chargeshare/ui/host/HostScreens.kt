package app.chargeshare.ui.host

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import app.chargeshare.ui.LocalTabBarOverlap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.chargeshare.presentation.host.HostDashboardUiState
import app.chargeshare.platform.UrlOpener
import app.chargeshare.presentation.host.HostDashboardViewModel
import app.chargeshare.presentation.host.PayoutUiState
import app.chargeshare.presentation.host.PayoutViewModel
import app.chargeshare.presentation.host.shortName
import app.chargeshare.shared.model.ChargingSession
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AmberButton
import app.chargeshare.ui.components.EmptyBox
import app.chargeshare.ui.components.ErrorBox
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.HeroHeader
import app.chargeshare.ui.components.InfoPill
import app.chargeshare.ui.components.LoadingBox
import app.chargeshare.ui.components.SectionTitle
import app.chargeshare.ui.components.SurfaceCard
import app.chargeshare.ui.formatDateTime
import app.chargeshare.ui.formatEuro
import app.chargeshare.ui.formatPowerKw
import app.chargeshare.ui.formatPricePerKwh
import kotlinx.datetime.TimeZone

@Composable
fun HostDashboardRoute(
    viewModel: HostDashboardViewModel,
    payoutViewModel: PayoutViewModel,
    urlOpener: UrlOpener,
    userName: String,
    onAddWallbox: () -> Unit,
    onEditWallbox: (Wallbox) -> Unit,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    // Beim Einblenden neu laden – auch nach dem Anlegen einer Wallbox.
    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(payoutViewModel) { payoutViewModel.refresh() }
    val state by viewModel.state.collectAsState()
    val payoutState by payoutViewModel.state.collectAsState()
    // Der Onboarding-Link wird im Browser geöffnet; zurück kommt der Host über den Deep Link (siehe MainContent).
    LaunchedEffect(payoutState.onboardingUrl) {
        payoutState.onboardingUrl?.let { payoutViewModel.onOnboardingUrlHandled(urlOpener.open(it)) }
    }
    HostDashboardScreen(
        state = state,
        payoutState = payoutState,
        onSetUpPayouts = payoutViewModel::onSetUpClicked,
        onCheckPayouts = payoutViewModel::onCheckStatus,
        userName = userName,
        zone = zone,
        onAddWallbox = onAddWallbox,
        onEditWallbox = onEditWallbox,
        onOnlineToggled = viewModel::onOnlineToggled,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/** Host-Dashboard: Einnahmen, eigene Wallboxen mit Freigabe-Schalter, letzte Sessions. */
@Composable
fun HostDashboardScreen(
    state: HostDashboardUiState,
    payoutState: PayoutUiState,
    onSetUpPayouts: () -> Unit,
    onCheckPayouts: () -> Unit,
    userName: String,
    zone: TimeZone,
    onAddWallbox: () -> Unit,
    onEditWallbox: (Wallbox) -> Unit,
    onOnlineToggled: (Wallbox, Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        HostDashboardHeader(
            userName = shortName(userName),
            subtitle = hostSubtitle(state.wallboxes),
            now = state.now,
            earnings = state.earnings,
        )
        when {
            state.isLoading && state.earnings == null -> LoadingBox()
            state.error != null && state.earnings == null -> ErrorBox(state.error, onRetry)
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                PayoutStatusCard(payoutState, onSetUp = onSetUpPayouts, onCheckStatus = onCheckPayouts)
                state.toggleError?.let { ErrorText(Strings.errorMessage(it)) }
                state.earnings?.takeIf { it.week.isNotEmpty() }?.let { WeeklyEarningsCard(it.week, state.now?.date) }
                AmberButton(Strings.AddWallbox, onAddWallbox)
                SectionTitle(Strings.MyWallboxes)
                if (state.wallboxes.isEmpty()) {
                    EmptyBox(Strings.NoMyWallboxes)
                } else {
                    state.wallboxes.forEach { wallbox ->
                        HostWallboxCard(wallbox, isToggling = state.togglingId == wallbox.id, onEdit = { onEditWallbox(wallbox) }) { onOnlineToggled(wallbox, it) }
                    }
                }
                SectionTitle(Strings.RecentSessions)
                if (state.sessions.isEmpty()) EmptyBox(Strings.NoSessions) else state.sessions.forEach { SessionRow(it, zone) }
                Text(Strings.PayoutNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Platz für eine darüberliegende Tab-Leiste (iOS), damit der letzte Eintrag hochgescrollt werden kann.
                Spacer(Modifier.height(LocalTabBarOverlap.current))
            }
        }
    }
}

private fun hostSubtitle(wallboxes: List<Wallbox>): String {
    val active = wallboxes.filter { it.isOnline }
    return when {
        wallboxes.isEmpty() -> Strings.NoHostWallboxSubtitle
        active.isEmpty() -> Strings.AllWallboxesBlockedSubtitle
        else -> Strings.hostSubtitle(active.size, active.first().city)
    }
}

@Composable
private fun HostWallboxCard(wallbox: Wallbox, isToggling: Boolean, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    SurfaceCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(wallbox.name, style = MaterialTheme.typography.titleMedium)
                Text(wallbox.fullAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${formatPowerKw(wallbox.powerKw)} · ${wallbox.connectorType.displayName} · ${formatPricePerKwh(wallbox.pricePerKwh)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onEdit) { Text(Strings.EditWallbox) }
            }
            Column(horizontalAlignment = Alignment.End) {
                InfoPill(if (wallbox.isOnline) Strings.Released else Strings.Blocked)
                Switch(
                    checked = wallbox.isOnline,
                    onCheckedChange = onToggle,
                    enabled = !isToggling,
                    modifier = Modifier.semantics { contentDescription = Strings.ReleaseSwitchDescription },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: ChargingSession, zone: TimeZone) {
    SurfaceCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(Strings.sessionTitle(session.driver?.name), style = MaterialTheme.typography.titleSmall)
                (session.startedAt ?: session.createdAt).let {
                    Text(formatDateTime(it, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val kwh = session.booking?.actualKwh
            Column(horizontalAlignment = Alignment.End) {
                Text(Strings.sessionKwh(kwh), style = MaterialTheme.typography.bodyMedium)
                session.booking?.totalAmount?.let { Text("+${formatEuro(it)}", style = MaterialTheme.typography.titleSmall, color = ChargeShareColors.PrimaryText) }
            }
        }
    }
}

/** Aufruf für Nutzer ohne Host-Rolle: Erklärung und „Host werden“. */
@Composable
fun BecomeHostScreen(
    isBusy: Boolean,
    errorMessage: String?,
    onBecomeHost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        HeroHeader(title = Strings.HostTitle)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Large)
                .padding(bottom = LocalTabBarOverlap.current),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(Strings.BecomeHostTitle, style = MaterialTheme.typography.headlineSmall, color = ChargeShareColors.PrimaryText, textAlign = TextAlign.Center)
            Text(Strings.BecomeHostBody, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            errorMessage?.let { ErrorText(it) }
            AmberButton(Strings.BecomeHost, onBecomeHost, isLoading = isBusy)
        }
    }
}
