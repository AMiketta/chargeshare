package app.chargeshare.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.chargeshare.payment.PaymentLauncher
import app.chargeshare.payment.saveCardCatching
import app.chargeshare.presentation.profile.AddCardState
import app.chargeshare.presentation.profile.SavedCardsUiState
import app.chargeshare.presentation.profile.SavedCardsViewModel
import app.chargeshare.shared.model.SavedCard
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AmberButton
import app.chargeshare.ui.components.ErrorBox
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.HeroHeader
import app.chargeshare.ui.components.LoadingBox
import app.chargeshare.ui.components.SurfaceCard
import kotlinx.coroutines.launch

@Composable
fun SavedCardsRoute(
    viewModel: SavedCardsViewModel,
    paymentLauncher: PaymentLauncher,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    // Eigener Scope: der Effekt startet nach `onSheetOpened()` neu, das offene Kartenformular darf dabei nicht abbrechen.
    val scope = rememberCoroutineScope()
    val request = (state.addCard as? AddCardState.Ready)?.request
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        viewModel.onSheetOpened()
        scope.launch { viewModel.onSaveCardResult(paymentLauncher.saveCardCatching(request)) }
    }
    SavedCardsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onAddCard = viewModel::onAddCard,
        onRemoveRequested = viewModel::onRemoveRequested,
        onRemoveConfirmed = viewModel::onRemoveConfirmed,
        onRemoveDismissed = viewModel::onRemoveDismissed,
        modifier = modifier,
    )
}

/** Gespeicherte Karten: Liste mit Entfernen und „Karte hinzufügen“. */
@Composable
fun SavedCardsScreen(
    state: SavedCardsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddCard: () -> Unit,
    onRemoveRequested: (SavedCard) -> Unit,
    onRemoveConfirmed: () -> Unit,
    onRemoveDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        HeroHeader(title = Strings.PaymentMethodsTitle, onBack = onBack)
        when {
            state.isLoading && state.cards.isEmpty() -> LoadingBox()
            state.error != null -> ErrorBox(state.error, onRetry)
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                if (!state.supported) {
                    Text(Strings.SavedCardsUnsupported, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(Strings.SavedCardsHint, style = MaterialTheme.typography.bodySmall, color = ChargeShareColors.OnSurfaceVariant)
                    if (state.cards.isEmpty()) Text(Strings.SavedCardsEmpty, style = MaterialTheme.typography.bodyMedium)
                    state.cards.forEach { card -> CardRow(card, enabled = !state.isBusy, isRemoving = state.removingId == card.id, onRemove = { onRemoveRequested(card) }) }
                    state.actionError?.let { ErrorText(Strings.errorMessage(it)) }
                    if (state.addCardFailed) ErrorText(Strings.AddCardFailed)
                    if (state.canAddCard) {
                        AmberButton(
                            text = if (state.addCard == AddCardState.Preparing) Strings.AddCardPreparing else Strings.AddCard,
                            onClick = onAddCard,
                            enabled = !state.isBusy,
                        )
                    } else {
                        Text(Strings.SavedCardsAddUnsupported, style = MaterialTheme.typography.bodySmall, color = ChargeShareColors.OnSurfaceVariant)
                    }
                }
            }
        }
    }
    state.removeCandidate?.let { card ->
        AlertDialog(
            onDismissRequest = onRemoveDismissed,
            title = { Text(Strings.RemoveCardTitle) },
            text = { Text(Strings.removeCardBody(Strings.cardLabel(card.brand, card.last4))) },
            confirmButton = { TextButton(onClick = onRemoveConfirmed) { Text(Strings.RemoveCardConfirm, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onRemoveDismissed) { Text(Strings.Cancel) } },
        )
    }
}

@Composable
private fun CardRow(card: SavedCard, enabled: Boolean, isRemoving: Boolean, onRemove: () -> Unit) {
    SurfaceCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(Strings.cardLabel(card.brand, card.last4), style = MaterialTheme.typography.titleMedium)
                Text(
                    Strings.cardExpiry(card.expMonth, card.expYear),
                    style = MaterialTheme.typography.bodySmall,
                    color = ChargeShareColors.OnSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove, enabled = enabled) {
                Text(if (isRemoving) "…" else Strings.RemoveCard, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
