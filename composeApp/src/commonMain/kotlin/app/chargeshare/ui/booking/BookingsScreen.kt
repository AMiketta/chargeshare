package app.chargeshare.ui.booking

import app.chargeshare.ui.LocalTabBarOverlap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.chargeshare.data.AppError
import app.chargeshare.payment.PaymentLauncher
import app.chargeshare.platform.ReceiptSharer
import app.chargeshare.payment.payCatching
import app.chargeshare.presentation.booking.BookingFilter
import app.chargeshare.presentation.booking.PayNowState
import app.chargeshare.presentation.booking.ReceiptState
import app.chargeshare.presentation.booking.ReviewDraft
import app.chargeshare.presentation.booking.hasReceipt
import app.chargeshare.presentation.booking.isReviewable
import app.chargeshare.presentation.booking.PaymentIssue
import app.chargeshare.presentation.booking.PaymentStep
import app.chargeshare.presentation.booking.PaymentUnavailableReason
import app.chargeshare.presentation.booking.bookingId
import app.chargeshare.presentation.booking.isBusy
import app.chargeshare.presentation.booking.isPayable
import app.chargeshare.presentation.booking.BookingsUiState
import app.chargeshare.presentation.booking.BookingsViewModel
import app.chargeshare.presentation.booking.isCancellable
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.CancelReasons
import app.chargeshare.shared.model.ReviewLimits
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AmberButton
import app.chargeshare.ui.components.ChoiceChips
import app.chargeshare.ui.components.EmptyBox
import app.chargeshare.ui.components.ErrorBox
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.FormTextField
import app.chargeshare.ui.components.HeroHeader
import app.chargeshare.ui.components.InfoPill
import app.chargeshare.ui.components.LoadingBox
import app.chargeshare.ui.components.SurfaceCard
import app.chargeshare.ui.formatDateTime
import app.chargeshare.ui.formatEuro
import app.chargeshare.ui.formatTime
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

@Composable
fun BookingsRoute(
    viewModel: BookingsViewModel,
    paymentLauncher: PaymentLauncher,
    receiptSharer: ReceiptSharer,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    // Beim Einblenden des Tabs neu laden, damit frisch gebuchte/stornierte Slots sichtbar sind.
    LaunchedEffect(viewModel) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    PayNowSheetEffect(state.payNow, viewModel, paymentLauncher)
    ReceiptEffect(state.receipt, viewModel, receiptSharer)
    BookingsScreen(
        state = state,
        zone = zone,
        onFilterChange = viewModel::onFilterChange,
        onCancelRequested = viewModel::onCancelRequested,
        onCancelConfirmed = viewModel::onCancelConfirmed,
        onCancelDismissed = viewModel::onCancelDismissed,
        onRetry = viewModel::refresh,
        onReviewRequested = viewModel::onReviewRequested,
        onReviewRatingChanged = viewModel::onReviewRatingChanged,
        onReviewCommentChanged = viewModel::onReviewCommentChanged,
        onReviewSubmitted = viewModel::onReviewSubmitted,
        onReviewDismissed = viewModel::onReviewDismissed,
        onPayNow = viewModel::onPayNow,
        onRetryPayNow = viewModel::onRetryPayNow,
        onPayNowDismissed = viewModel::onPayNowDismissed,
        canShareReceipt = receiptSharer.isSupported,
        onReceiptRequested = viewModel::onReceiptRequested,
        onReceiptErrorShown = viewModel::onReceiptErrorShown,
        modifier = modifier,
    )
}

/** Gibt die geladene Quittung über die Plattform aus und meldet das Ergebnis zurück. */
@Composable
private fun ReceiptEffect(receipt: ReceiptState, viewModel: BookingsViewModel, receiptSharer: ReceiptSharer) {
    LaunchedEffect(receipt) {
        if (receipt is ReceiptState.Ready) viewModel.onReceiptHandled(receiptSharer.share(receipt.fileName, receipt.pdf))
    }
}

/**
 * Öffnet das Zahlungsfenster der Plattform, sobald das ViewModel [PayNowState.PaymentRequired] meldet.
 * Eigener Scope aus demselben Grund wie im Buchungsflow: der Effekt startet nach `onPaymentSheetOpened()` neu,
 * das offene Fenster darf dabei nicht abgebrochen werden.
 */
@Composable
private fun PayNowSheetEffect(payNow: PayNowState, viewModel: BookingsViewModel, paymentLauncher: PaymentLauncher) {
    val scope = rememberCoroutineScope()
    val requested = (payNow as? PayNowState.PaymentRequired)?.request
    LaunchedEffect(requested) {
        if (requested == null) return@LaunchedEffect
        viewModel.onPaymentSheetOpened()
        scope.launch { viewModel.onPaymentResult(paymentLauncher.payCatching(requested)) }
    }
}

/** Buchungsliste (kommend/vergangen) mit Stornieren. */
@Composable
fun BookingsScreen(
    state: BookingsUiState,
    zone: TimeZone,
    onFilterChange: (BookingFilter) -> Unit,
    onCancelRequested: (Booking) -> Unit,
    onCancelConfirmed: () -> Unit,
    onCancelDismissed: () -> Unit,
    onRetry: () -> Unit,
    onReviewRequested: (Booking) -> Unit,
    onReviewRatingChanged: (Int) -> Unit,
    onReviewCommentChanged: (String) -> Unit,
    onReviewSubmitted: () -> Unit,
    onReviewDismissed: () -> Unit,
    onPayNow: (Booking) -> Unit,
    onRetryPayNow: () -> Unit,
    onPayNowDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    canShareReceipt: Boolean = false,
    onReceiptRequested: (Booking) -> Unit = {},
    onReceiptErrorShown: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        HeroHeader(title = Strings.BookingsTitle)
        ChoiceChips(
            options = BookingFilter.entries,
            selected = state.filter,
            label = { if (it == BookingFilter.Upcoming) Strings.BookingsUpcoming else Strings.BookingsPast },
            onSelected = onFilterChange,
            modifier = Modifier.padding(vertical = Spacing.Small),
        )
        state.cancelError?.let { ErrorText(Strings.errorMessage(it), Modifier.padding(horizontal = Spacing.Medium)) }
        when {
            state.isLoading && state.bookings.isEmpty() -> LoadingBox()
            state.error != null -> ErrorBox(state.error, onRetry)
            state.visibleBookings.isEmpty() ->
                EmptyBox(if (state.filter == BookingFilter.Upcoming) Strings.NoUpcomingBookings else Strings.NoPastBookings)
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = Spacing.Medium,
                    top = Spacing.Medium,
                    end = Spacing.Medium,
                    bottom = Spacing.Medium + LocalTabBarOverlap.current,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                items(state.visibleBookings, key = { it.id }) { booking ->
                    BookingCard(
                        booking = booking,
                        zone = zone,
                        isCancelling = state.cancellingId == booking.id,
                        payment = BookingPaymentUi(
                            canPay = state.canPayOnline && booking.isPayable(state.now),
                            payNow = state.payNow.takeIf { it.bookingId == booking.id } ?: PayNowState.Idle,
                            isAnyPaymentBusy = state.payNow.isBusy,
                        ),
                        onCancel = { onCancelRequested(booking) },
                        onReview = { onReviewRequested(booking) },
                        onPayNow = { onPayNow(booking) },
                        onRetryPayNow = onRetryPayNow,
                        onPayNowDismissed = onPayNowDismissed,
                        receiptAvailable = canShareReceipt && booking.hasReceipt(),
                        isReceiptLoading = (state.receipt as? ReceiptState.Loading)?.bookingId == booking.id,
                        onReceipt = { onReceiptRequested(booking) },
                    )
                }
            }
        }
    }
    state.reviewDraft?.let { draft ->
        ReviewDialog(draft, onReviewRatingChanged, onReviewCommentChanged, onReviewSubmitted, onReviewDismissed)
    }
    (state.receipt as? ReceiptState.Failed)?.let { failed ->
        AlertDialog(
            onDismissRequest = onReceiptErrorShown,
            title = { Text(Strings.ReceiptAction) },
            text = { Text(Strings.receiptErrorMessage(failed.error)) },
            confirmButton = { TextButton(onClick = onReceiptErrorShown) { Text(Strings.Close) } },
        )
    }
    state.cancelCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = onCancelDismissed,
            title = { Text(Strings.CancelBookingTitle) },
            text = { Text(Strings.cancelBookingBody(candidate.wallbox?.name ?: Strings.UnknownWallbox)) },
            confirmButton = { TextButton(onClick = onCancelConfirmed) { Text(Strings.CancelBookingConfirm) } },
            dismissButton = { TextButton(onClick = onCancelDismissed) { Text(Strings.CancelBookingKeep) } },
        )
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    zone: TimeZone,
    isCancelling: Boolean,
    payment: BookingPaymentUi,
    onCancel: () -> Unit,
    onReview: () -> Unit,
    onPayNow: () -> Unit,
    onRetryPayNow: () -> Unit,
    onPayNowDismissed: () -> Unit,
    receiptAvailable: Boolean,
    isReceiptLoading: Boolean,
    onReceipt: () -> Unit,
) {
    SurfaceCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(booking.wallbox?.name ?: Strings.UnknownWallbox, style = MaterialTheme.typography.titleMedium)
                booking.wallbox?.let { Text(it.fullAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            StatusPill(booking.status)
        }
        Text(
            "${formatDateTime(booking.startTime, zone)} – ${formatTime(booking.endTime, zone)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        booking.totalAmount?.let { Text(formatEuro(it), style = MaterialTheme.typography.titleMedium, color = ChargeShareColors.PrimaryText) }
        if (booking.status == BookingStatus.PENDING) {
            Text(Strings.PendingPaymentHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (booking.cancelReason == CancelReasons.PAYMENT_EXPIRED) {
            Text(Strings.PaymentExpiredHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (payment.canPay) PayNowSection(payment.payNow, onPayNow, onRetryPayNow, onPayNowDismissed)
        if (booking.isReviewable()) {
            TextButton(onClick = onReview) { Text(Strings.ReviewAction) }
        } else if (booking.status == BookingStatus.COMPLETED && booking.reviewed) {
            Text(Strings.Reviewed, style = MaterialTheme.typography.bodySmall, color = ChargeShareColors.PrimaryText)
        }
        if (receiptAvailable) {
            TextButton(onClick = onReceipt, enabled = !isReceiptLoading) {
                Text(if (isReceiptLoading) Strings.ReceiptLoading else Strings.ReceiptAction)
            }
        }
        if (booking.isCancellable()) {
            TextButton(onClick = onCancel, enabled = !isCancelling && !payment.isAnyPaymentBusy) {
                Text(Strings.CancelBooking, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Bewertungsdialog: Sterne (Pflicht) und Kommentar (optional). */
@Composable
private fun ReviewDialog(
    draft: ReviewDraft,
    onRatingChanged: (Int) -> Unit,
    onCommentChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.ReviewTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Text(Strings.reviewBody(draft.booking.wallbox?.name ?: Strings.UnknownWallbox), style = MaterialTheme.typography.bodyMedium)
                StarRatingInput(draft.rating, onRatingChanged, enabled = !draft.isSubmitting)
                FormTextField(
                    value = draft.comment,
                    onValueChange = onCommentChanged,
                    label = Strings.ReviewCommentLabel,
                    errorText = Strings.ReviewCommentTooLong.takeIf { draft.isCommentTooLong },
                    supportingText = Strings.reviewCommentCounter(draft.comment.length, ReviewLimits.MAX_COMMENT_LENGTH),
                    enabled = !draft.isSubmitting,
                    singleLine = false,
                )
                draft.error?.let { ErrorText(Strings.errorMessage(it)) }
            }
        },
        confirmButton = { TextButton(onClick = onSubmit, enabled = draft.canSubmit) { Text(Strings.ReviewSubmit) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !draft.isSubmitting) { Text(Strings.Cancel) } },
    )
}

/** Fünf antippbare Sterne; jeder Stern hat eine eigene Beschreibung für Screenreader. */
@Composable
private fun StarRatingInput(rating: Int, onRatingChanged: (Int) -> Unit, enabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
        (ReviewLimits.MIN_RATING..ReviewLimits.MAX_RATING).forEach { stars ->
            IconButton(onClick = { onRatingChanged(stars) }, enabled = enabled) {
                Icon(
                    AppIcons.Star,
                    contentDescription = Strings.reviewStarOption(stars),
                    tint = if (stars <= rating) ChargeShareColors.Accent else ChargeShareColors.OnSurfaceMuted,
                )
            }
        }
    }
}

/** Was die Karte einer Buchung über das Bezahlen wissen muss. */
private data class BookingPaymentUi(val canPay: Boolean, val payNow: PayNowState, val isAnyPaymentBusy: Boolean)

/** „Jetzt bezahlen“ mit laufendem Zustand, Fehlerhinweis und „Erneut versuchen“. */
@Composable
private fun PayNowSection(payNow: PayNowState, onPayNow: () -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val message = payNowMessage(payNow)
    if (message != null) ErrorText(message)
    when (payNow) {
        PayNowState.Idle -> AmberButton(text = Strings.PayNow, onClick = onPayNow)
        is PayNowState.Preparing, is PayNowState.PaymentRequired -> AmberButton(Strings.PaymentInProgressSheet, onClick = {}, enabled = false, isLoading = true)
        is PayNowState.InProgress -> AmberButton(
            text = if (payNow.step == PaymentStep.CONFIRMING) Strings.PaymentInProgressConfirming else Strings.PaymentInProgressSheet,
            onClick = {},
            enabled = false,
            isLoading = true,
        )
        is PayNowState.Incomplete -> {
            val confirmOnly = payNow.issue is PaymentIssue.ConfirmationFailed
            AmberButton(text = if (confirmOnly) Strings.CheckPayment else Strings.PayAgain, onClick = onRetry)
            TextButton(onClick = onDismiss) { Text(Strings.Close) }
        }
        is PayNowState.PrepareFailed -> {
            AmberButton(text = Strings.PayAgain, onClick = onRetry)
            TextButton(onClick = onDismiss) { Text(Strings.Close) }
        }
        is PayNowState.Unavailable -> TextButton(onClick = onDismiss) { Text(Strings.Close) }
    }
}

private fun payNowMessage(payNow: PayNowState): String? = when (payNow) {
    PayNowState.Idle, is PayNowState.Preparing, is PayNowState.PaymentRequired, is PayNowState.InProgress -> null
    is PayNowState.Incomplete -> when (val issue = payNow.issue) {
        PaymentIssue.Canceled -> Strings.PaymentCanceled
        PaymentIssue.Failed -> Strings.PaymentFailed
        is PaymentIssue.ConfirmationFailed ->
            if (issue.error == AppError.PaymentRequired || issue.error == AppError.Network) Strings.PaymentNotConfirmed else Strings.errorMessage(issue.error)
    }
    is PayNowState.PrepareFailed -> Strings.errorMessage(payNow.error)
    is PayNowState.Unavailable -> when (payNow.reason) {
        PaymentUnavailableReason.PLATFORM_NOT_SUPPORTED -> Strings.PaymentPlatformUnsupported
        PaymentUnavailableReason.SERVER_NOT_CONFIGURED -> Strings.PaymentServerNotConfigured
    }
}

@Composable
private fun StatusPill(status: BookingStatus) {
    val isProblem = status == BookingStatus.CANCELLED || status == BookingStatus.FAILED
    val isPending = status == BookingStatus.PENDING
    InfoPill(
        text = Strings.bookingStatus(status),
        containerColor = when {
            isProblem -> ChargeShareColors.ErrorLight
            isPending -> ChargeShareColors.AccentLight
            else -> ChargeShareColors.PrimarySurface
        },
        contentColor = when {
            isProblem -> ChargeShareColors.Error
            isPending -> ChargeShareColors.OnAccentLight
            else -> ChargeShareColors.PrimaryText
        },
    )
}
