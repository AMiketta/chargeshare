package app.chargeshare.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.chargeshare.payment.PaymentLauncher
import app.chargeshare.payment.PaymentMethodChoice
import app.chargeshare.payment.payCatching
import app.chargeshare.presentation.booking.BookingFlowUiState
import app.chargeshare.presentation.booking.BookingFlowViewModel
import app.chargeshare.presentation.booking.BookingSubmitState
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AmberButton
import app.chargeshare.ui.components.AppCard
import app.chargeshare.ui.components.EmptyBox
import app.chargeshare.ui.components.ErrorBox
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.HeroHeader
import app.chargeshare.ui.components.LoadingBox
import app.chargeshare.ui.components.PrimaryButton
import app.chargeshare.ui.components.SectionTitle
import app.chargeshare.ui.components.SurfaceCard
import app.chargeshare.ui.formatDate
import app.chargeshare.ui.formatDateTime
import app.chargeshare.ui.formatEuro
import app.chargeshare.ui.formatPricePerKwh
import app.chargeshare.ui.formatTime
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

@Composable
fun BookingFlowRoute(
    viewModel: BookingFlowViewModel,
    paymentLauncher: PaymentLauncher,
    onBack: () -> Unit,
    onShowBookings: () -> Unit,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val state by viewModel.state.collectAsState()
    PaymentSheetEffect(state.submit, viewModel, paymentLauncher)
    BookingFlowScreen(
        state = state,
        zone = zone,
        onBack = onBack,
        onDaySelected = viewModel::onDaySelected,
        onStartSelected = viewModel::onStartSelected,
        onDurationSelected = viewModel::onDurationSelected,
        onRetrySlots = viewModel::onRetrySlots,
        onSubmit = viewModel::onSubmit,
        onPaymentMethodSelected = viewModel::onPaymentMethodSelected,
        onRetryPaymentConfig = viewModel::onRetryPaymentConfig,
        onRetryPayment = viewModel::onRetryPayment,
        onDiscardBooking = viewModel::onDiscardBooking,
        onShowBookings = onShowBookings,
        modifier = modifier,
    )
}

/**
 * Öffnet das Zahlungsfenster der Plattform, sobald das ViewModel [BookingSubmitState.PaymentRequired] meldet.
 * Der Aufruf läuft in einem eigenen Scope, weil der Effekt nach `onPaymentSheetOpened()` neu startet (Zustandswechsel)
 * und das offene Fenster dabei nicht abgebrochen werden darf.
 */
@Composable
private fun PaymentSheetEffect(submit: BookingSubmitState, viewModel: BookingFlowViewModel, paymentLauncher: PaymentLauncher) {
    val scope = rememberCoroutineScope()
    val requested = (submit as? BookingSubmitState.PaymentRequired)?.request
    LaunchedEffect(requested) {
        if (requested == null) return@LaunchedEffect
        viewModel.onPaymentSheetOpened()
        scope.launch { viewModel.onPaymentResult(paymentLauncher.payCatching(requested)) }
    }
}

/** Buchungsflow: Tag, Startzeit und Dauer wählen, Kosten ansehen, buchen – danach die Bestätigung. */
@Composable
fun BookingFlowScreen(
    state: BookingFlowUiState,
    zone: TimeZone,
    onBack: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    onStartSelected: (Instant) -> Unit,
    onDurationSelected: (Int) -> Unit,
    onRetrySlots: () -> Unit,
    onSubmit: () -> Unit,
    onPaymentMethodSelected: (PaymentMethodChoice) -> Unit,
    onRetryPaymentConfig: () -> Unit,
    onRetryPayment: () -> Unit,
    onDiscardBooking: () -> Unit,
    onShowBookings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val submit = state.submit
    if (submit is BookingSubmitState.Success) {
        BookingConfirmation(submit.booking, state, zone, onShowBookings, onBack, modifier)
        return
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            HeroHeader(
                title = Strings.BookingTitle,
                subtitle = "${state.wallbox.name} · ${formatPricePerKwh(state.wallbox.pricePerKwh)}",
                onBack = onBack,
            )
        },
        bottomBar = {
            Surface(shadowElevation = Spacing.Small) {
                BookingActionBar(state, onSubmit, onRetryPayment, onDiscardBooking, Modifier.padding(Spacing.Medium))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            BookingSection(Strings.Date, Modifier.padding(horizontal = Spacing.Medium), edgeToEdge = true) {
                DateScroller(state.days, state.selectedDay, onDaySelected)
            }
            BookingSection(Strings.StartTime, Modifier.padding(horizontal = Spacing.Medium)) {
                SlotSection(state, zone, onStartSelected, onRetrySlots)
            }
            if (state.durationOptions.isNotEmpty()) {
                BookingSection(Strings.DurationLabel, Modifier.padding(horizontal = Spacing.Medium)) {
                    DurationPills(state.durationOptions, state.selectedDurationMinutes, onDurationSelected)
                }
            }
            EstimateCard(state, Modifier.padding(horizontal = Spacing.Medium))
            PaymentMethodSection(
                setup = state.paymentSetup,
                selected = state.selectedPaymentMethod,
                onSelected = onPaymentMethodSelected,
                onRetry = onRetryPaymentConfig,
                modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            )
        }
    }
}

@Composable
private fun SlotSection(
    state: BookingFlowUiState,
    zone: TimeZone,
    onStartSelected: (Instant) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.isLoadingSlots -> LoadingBox(Modifier.padding(Spacing.Large))
        state.slotsError != null -> ErrorBox(state.slotsError, onRetry)
        state.freeStarts.isEmpty() && state.lockedStarts.isEmpty() -> EmptyBox(Strings.NoFreeSlots)
        else -> StartTimeGrid(state.freeStarts, state.lockedStarts, state.selectedStart, zone, onStartSelected)
    }
}

/**
 * Abschnitt im Buchungsflow: Karte mit Versalien-Label (Design: `.booking-section`).
 * Mit [edgeToEdge] bekommt der Inhalt keinen seitlichen Rand (z. B. für den scrollenden Datumsstreifen).
 */
@Composable
private fun BookingSection(
    label: String,
    modifier: Modifier = Modifier,
    edgeToEdge: Boolean = false,
    content: @Composable () -> Unit,
) {
    val inset = Spacing.Medium - Spacing.XSmall
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = inset), verticalArrangement = Arrangement.spacedBy(Spacing.Small + Spacing.XSmall / 2)) {
            BookingSectionLabel(label, Modifier.padding(horizontal = inset))
            Box(Modifier.padding(horizontal = if (edgeToEdge) 0.dp else inset)) { content() }
        }
    }
}

/** Kostenschätzung als Amber-Karte (Design: Hinweisfläche in `AccentLight`). */
@Composable
private fun EstimateCard(state: BookingFlowUiState, modifier: Modifier = Modifier) {
    val estimate = state.estimate
    AppCard(modifier = modifier.fillMaxWidth(), containerColor = ChargeShareColors.AccentLight) {
        Column(modifier = Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            BookingSectionLabel(Strings.CostEstimate, Modifier.padding(bottom = Spacing.XSmall))
            if (estimate == null) {
                Text(Strings.SelectSlotFirst, style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.OnSurfaceVariant)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Strings.estimateLine(estimate.kwh, state.wallbox.pricePerKwh), style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.OnSurfaceVariant)
                    Text(formatEuro(estimate.totalEur), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = ChargeShareColors.Accent.copy(alpha = AMBER_DIVIDER_ALPHA))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.Total, style = MaterialTheme.typography.titleSmall)
                    Text(formatEuro(estimate.totalEur), style = MaterialTheme.typography.titleLarge, color = ChargeShareColors.AccentDark)
                }
                Text(Strings.CostDisclaimer, style = MaterialTheme.typography.bodySmall, color = ChargeShareColors.OnSurfaceVariant)
            }
        }
    }
}

private const val AMBER_DIVIDER_ALPHA = 0.4f

@Composable
private fun BookingConfirmation(
    booking: Booking,
    state: BookingFlowUiState,
    zone: TimeZone,
    onShowBookings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPending = booking.status == BookingStatus.PENDING
    Column(modifier = modifier.fillMaxSize()) {
        HeroHeader(title = if (isPending) Strings.BookingPendingTitle else Strings.BookingSuccessTitle)
        Column(
            modifier = Modifier.padding(Spacing.Medium).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Icon(AppIcons.Check, contentDescription = null, tint = ChargeShareColors.Available)
            SurfaceCard {
                Text(state.wallbox.name, style = MaterialTheme.typography.titleMedium)
                Text(state.wallbox.fullAddress, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${formatDateTime(booking.startTime, zone)} – ${formatTime(booking.endTime, zone)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                booking.totalAmount?.let { Text(formatEuro(it), style = MaterialTheme.typography.titleMedium, color = ChargeShareColors.Primary) }
                Text(Strings.bookingStatus(booking.status), style = MaterialTheme.typography.labelLarge)
            }
            if (isPending) {
                Text(Strings.BookingPendingBody, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            AmberButton(Strings.ToMyBookings, onShowBookings)
            PrimaryButton(Strings.Close, onClose)
        }
    }
}

