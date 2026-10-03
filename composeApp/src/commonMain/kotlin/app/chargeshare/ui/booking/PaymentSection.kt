package app.chargeshare.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.chargeshare.data.AppError
import app.chargeshare.payment.PaymentMethodChoice
import app.chargeshare.presentation.booking.BookingFlowUiState
import app.chargeshare.presentation.booking.BookingSubmitState
import app.chargeshare.presentation.booking.PaymentIssue
import app.chargeshare.presentation.booking.PaymentSetup
import app.chargeshare.presentation.booking.PaymentStep
import app.chargeshare.presentation.booking.PaymentUnavailableReason
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AmberButton
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.SectionTitle
import app.chargeshare.ui.components.SurfaceCard

/** Karte „Zahlungsart“ im Buchungsflow: Auswahl (Google Pay / Apple Pay / Karte) oder ein klarer Hinweis, warum nicht bezahlt werden kann. */
@Composable
internal fun PaymentMethodSection(
    setup: PaymentSetup,
    selected: PaymentMethodChoice?,
    onSelected: (PaymentMethodChoice) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier) {
        SectionTitle(Strings.PaymentTitle)
        when (setup) {
            PaymentSetup.Loading -> Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(Spacing.XSmall))
                Text(Strings.PaymentLoading, style = MaterialTheme.typography.bodyMedium)
            }
            PaymentSetup.TestPayment -> HintText(Strings.PaymentTestMode)
            is PaymentSetup.Ready -> MethodChoices(setup.methods, selected, onSelected)
            is PaymentSetup.Unavailable -> ErrorText(
                when (setup.reason) {
                    PaymentUnavailableReason.PLATFORM_NOT_SUPPORTED -> Strings.PaymentPlatformUnsupported
                    PaymentUnavailableReason.SERVER_NOT_CONFIGURED -> Strings.PaymentServerNotConfigured
                },
            )
            is PaymentSetup.LoadFailed -> {
                ErrorText(Strings.errorMessage(setup.error))
                TextButton(onClick = onRetry) { Text(Strings.Retry) }
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MethodChoices(
    methods: List<PaymentMethodChoice>,
    selected: PaymentMethodChoice?,
    onSelected: (PaymentMethodChoice) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        methods.forEach { method ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = Spacing.MinTouchTarget)
                    .selectable(selected = method == selected, onClick = { onSelected(method) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                // Die ganze Zeile ist anklickbar (selectable), der RadioButton zeigt nur den Zustand.
                RadioButton(selected = method == selected, onClick = null)
                Text(Strings.paymentMethod(method), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Untere Leiste des Buchungsflows: Buchen-Button, laufende Zahlung oder „Erneut bezahlen / Verwerfen“. */
@Composable
internal fun BookingActionBar(
    state: BookingFlowUiState,
    onSubmit: () -> Unit,
    onRetryPayment: () -> Unit,
    onDiscardBooking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val submit = state.submit
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        when (submit) {
            is BookingSubmitState.PaymentIncomplete -> IncompletePayment(submit, onRetryPayment, onDiscardBooking)
            is BookingSubmitState.PaymentRequired -> BusyButton(Strings.PaymentInProgressSheet)
            is BookingSubmitState.PaymentInProgress -> BusyButton(stepText(submit.step))
            else -> {
                if (submit is BookingSubmitState.Failed) ErrorText(Strings.errorMessage(submit.error))
                val total = state.estimate?.totalEur
                AmberButton(
                    text = if (state.paymentSetup is PaymentSetup.Ready) Strings.payAndBookWithPrice(total) else Strings.bookNowWithPrice(total),
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    isLoading = submit is BookingSubmitState.Submitting,
                )
            }
        }
    }
}

@Composable
private fun BusyButton(text: String) {
    AmberButton(text = text, onClick = {}, enabled = false, isLoading = true)
}

private fun stepText(step: PaymentStep): String = when (step) {
    PaymentStep.SHEET_OPEN -> Strings.PaymentInProgressSheet
    PaymentStep.CONFIRMING -> Strings.PaymentInProgressConfirming
    PaymentStep.DISCARDING -> Strings.PaymentInProgressDiscarding
}

@Composable
private fun IncompletePayment(
    submit: BookingSubmitState.PaymentIncomplete,
    onRetryPayment: () -> Unit,
    onDiscardBooking: () -> Unit,
) {
    ErrorText(issueMessage(submit.issue))
    submit.discardError?.let { ErrorText(Strings.errorMessage(it)) }
    val isConfirmationIssue = submit.issue is PaymentIssue.ConfirmationFailed
    AmberButton(text = if (isConfirmationIssue) Strings.CheckPayment else Strings.PayAgain, onClick = onRetryPayment)
    TextButton(onClick = onDiscardBooking, modifier = Modifier.fillMaxWidth()) { Text(Strings.DiscardBooking) }
}

private fun issueMessage(issue: PaymentIssue): String = when (issue) {
    PaymentIssue.Canceled -> Strings.PaymentCanceled
    PaymentIssue.Failed -> Strings.PaymentFailed
    is PaymentIssue.ConfirmationFailed -> confirmationMessage(issue.error)
}

private fun confirmationMessage(error: AppError): String =
    if (error == AppError.PaymentRequired || error == AppError.Network) Strings.PaymentNotConfirmed else Strings.errorMessage(error)
