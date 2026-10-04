package app.chargeshare.ui.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.chargeshare.presentation.host.PayoutUiState
import app.chargeshare.shared.model.PayoutState
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AmberButton
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.InfoPill
import app.chargeshare.ui.components.SurfaceCard

/**
 * Stripe-Auszahlungsstatus im Host-Dashboard: nicht eingerichtet / in Prüfung / bereit.
 * Der Status steht immer auch als Text im Label (nicht nur über Farbe).
 */
@Composable
fun PayoutStatusCard(
    state: PayoutUiState,
    onSetUp: () -> Unit,
    onCheckStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return
    SurfaceCard(modifier = modifier) {
        val content = payoutContent(state.state)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(content.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (content.statusLabel != null) InfoPill(content.statusLabel)
        }
        if (content.body != null) {
            Text(content.body, style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.OnSurfaceMuted)
        }
        state.error?.let { ErrorText(Strings.errorMessage(it)) }
        if (state.openFailed) ErrorText(Strings.PayoutOpenFailed)
        if (content.actionLabel != null) {
            AmberButton(content.actionLabel, onSetUp, isLoading = state.isStarting)
        }
        if (state.state == PayoutState.PENDING) {
            TextButton(onClick = onCheckStatus, enabled = !state.isLoading) { Text(Strings.PayoutCheckStatus) }
        }
    }
}

private data class PayoutCardContent(val title: String, val body: String?, val statusLabel: String?, val actionLabel: String?)

private fun payoutContent(state: PayoutState?): PayoutCardContent = when (state) {
    PayoutState.NOT_STARTED -> PayoutCardContent(
        Strings.PayoutNotStartedTitle, Strings.PayoutNotStartedBody, Strings.PayoutStatusNotStarted, Strings.PayoutSetUp,
    )
    PayoutState.PENDING -> PayoutCardContent(
        Strings.PayoutPendingTitle, Strings.PayoutPendingBody, Strings.PayoutStatusPending, Strings.PayoutContinue,
    )
    PayoutState.READY -> PayoutCardContent(Strings.PayoutReadyTitle, Strings.PayoutReadyBody, Strings.PayoutStatusReady, null)
    // Status nicht ladbar: nur Fehlertext, kein Aktionsknopf (Server unerreichbar).
    PayoutState.NOT_REQUIRED, null -> PayoutCardContent(Strings.PayoutNotStartedTitle, null, null, null)
}
