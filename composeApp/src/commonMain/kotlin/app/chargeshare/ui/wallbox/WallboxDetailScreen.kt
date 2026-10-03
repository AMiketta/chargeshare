package app.chargeshare.ui.wallbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.chargeshare.presentation.wallbox.WallboxDetailUiState
import app.chargeshare.presentation.wallbox.WallboxDetailViewModel
import app.chargeshare.shared.model.Review
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AmberButton
import app.chargeshare.ui.components.ErrorBox
import app.chargeshare.ui.components.HeroHeader
import app.chargeshare.ui.components.InfoPill
import app.chargeshare.ui.components.LoadingBox
import app.chargeshare.ui.components.SectionTitle
import app.chargeshare.ui.components.SurfaceCard
import app.chargeshare.ui.formatPowerKw
import app.chargeshare.ui.formatPricePerKwh
import app.chargeshare.ui.weekdayIndexFromKey
import app.chargeshare.ui.weekdayShortFromKey

@Composable
fun WallboxDetailRoute(
    viewModel: WallboxDetailViewModel,
    onBack: () -> Unit,
    onBook: (Wallbox) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    WallboxDetailScreen(state, onBack, onBook, viewModel::onRetry, modifier)
}

/** Detailseite: Eckdaten, Verfügbarkeit, Bewertungen und der Buchen-Button. */
@Composable
fun WallboxDetailScreen(
    state: WallboxDetailUiState,
    onBack: () -> Unit,
    onBook: (Wallbox) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wallbox = state.wallbox
    Scaffold(
        modifier = modifier,
        topBar = { HeroHeader(title = wallbox?.name ?: Strings.DetailTitle, subtitle = wallbox?.fullAddress, onBack = onBack) },
        bottomBar = {
            if (wallbox != null) {
                Surface(shadowElevation = Spacing.Small) {
                    Column(modifier = Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        if (!wallbox.isOnline) {
                            Text(Strings.OfflineCannotBook, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        AmberButton(Strings.BookNow, onClick = { onBook(wallbox) }, enabled = wallbox.isOnline)
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.error != null -> Column(Modifier.padding(padding)) { ErrorBox(state.error, onRetry) }
            wallbox != null -> DetailContent(wallbox, state, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(wallbox: Wallbox, state: WallboxDetailUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            AvailabilityPill(wallbox.isOnline)
            InfoPill(formatPowerKw(wallbox.powerKw))
            InfoPill(wallbox.connectorType.displayName)
            InfoPill(if (wallbox.isMetered) Strings.Metered else Strings.NotMetered)
        }
        SurfaceCard {
            Text(formatPricePerKwh(wallbox.pricePerKwh), style = MaterialTheme.typography.headlineSmall, color = ChargeShareColors.Primary)
            Text(Strings.ratingSummary(wallbox.ratingAvg, wallbox.ratingCount), style = MaterialTheme.typography.bodyMedium)
            Text(Strings.duration(wallbox.minDurationMin, wallbox.maxDurationMin), style = MaterialTheme.typography.bodyMedium)
            listOfNotNull(wallbox.manufacturer, wallbox.model).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        wallbox.description?.takeIf { it.isNotBlank() }?.let {
            SectionTitle(Strings.AboutWallbox)
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        AvailabilityCard(wallbox, state.freeSlotsToday)
        SectionTitle(Strings.Reviews)
        if (state.reviews.isEmpty()) {
            Text(Strings.NoReviews, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.reviews.forEach { ReviewItem(it) }
        }
    }
}

@Composable
private fun AvailabilityCard(wallbox: Wallbox, freeSlotsToday: Int?) {
    SurfaceCard {
        SectionTitle(Strings.Availability)
        freeSlotsToday?.let { Text(Strings.freeSlotsToday(it), style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.Primary) }
        if (wallbox.availability.isEmpty()) {
            Text(Strings.NoAvailability, style = MaterialTheme.typography.bodyMedium)
        } else {
            wallbox.availability.entries.sortedBy { weekdayIndexFromKey(it.key) }.forEach { (day, slots) ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                    Text(weekdayShortFromKey(day), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = Spacing.Small))
                    Text(slots.joinToString(", ") { "${it.start}–${it.end}" }, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(Strings.AvailabilityUtcNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewItem(review: Review) {
    SurfaceCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall),
            modifier = Modifier.semantics { contentDescription = Strings.ratingStars(review.rating) },
        ) {
            repeat(review.rating.coerceIn(0, MAX_STARS)) {
                Icon(AppIcons.Star, contentDescription = null, tint = ChargeShareColors.Accent)
            }
            Text(review.author?.name ?: Strings.ByAnonymous, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.Small))
        }
        review.comment?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

private const val MAX_STARS = 5
