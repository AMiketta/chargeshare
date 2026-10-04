package app.chargeshare.ui.wallbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.chargeshare.data.AppError
import app.chargeshare.data.commonWindow
import app.chargeshare.presentation.wallbox.FavoritesViewModel
import app.chargeshare.data.WALLBOX_ZONE
import app.chargeshare.data.utcToLocal
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
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.OutlineChip
import app.chargeshare.ui.components.PhotoImage
import app.chargeshare.ui.components.LoadingBox
import app.chargeshare.ui.components.SectionTitle
import app.chargeshare.ui.components.SurfaceCard
import app.chargeshare.ui.formatEuro
import app.chargeshare.ui.formatOpeningHours
import app.chargeshare.ui.formatPowerKw
import app.chargeshare.ui.weekdayIndexFromKey
import app.chargeshare.ui.weekdayShortFromKey
import kotlin.time.Clock

@Composable
fun WallboxDetailRoute(
    viewModel: WallboxDetailViewModel,
    favorites: FavoritesViewModel,
    onBack: () -> Unit,
    onBook: (Wallbox) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val favoritesState by favorites.state.collectAsState()
    WallboxDetailScreen(
        state = state,
        isFavorite = favoritesState.isFavorite(viewModel.wallboxId),
        favoriteError = favoritesState.error,
        onBack = onBack,
        onBook = onBook,
        onRetry = viewModel::onRetry,
        onToggleFavorite = { favorites.onToggle(viewModel.wallboxId) },
        onFavoriteErrorShown = favorites::onErrorShown,
        modifier = modifier,
    )
}

/** Detailseite: Hero mit Modell-Badge, Eckdaten, Host, Verfügbarkeit, Bewertungen und der Buchen-Button. */
@Composable
fun WallboxDetailScreen(
    state: WallboxDetailUiState,
    isFavorite: Boolean,
    favoriteError: AppError?,
    onBack: () -> Unit,
    onBook: (Wallbox) -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFavoriteErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wallbox = state.wallbox
    Scaffold(
        modifier = modifier,
        // Mit geladener Wallbox ist der Hero Teil des scrollenden Inhalts; davor und bei Fehlern genügt der einfache Kopf.
        topBar = { if (wallbox == null) HeroHeader(title = Strings.DetailTitle, onBack = onBack) },
        bottomBar = {
            if (wallbox != null) {
                Surface(shadowElevation = Spacing.Small) {
                    Column(modifier = Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        if (!wallbox.isOnline) {
                            Text(Strings.OfflineCannotBook, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        } else if (!wallbox.payoutsReady) {
                            Text(Strings.HostPayoutsPendingHint, style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.OnSurfaceVariant)
                        }
                        AmberButton(Strings.BookNow, onClick = { onBook(wallbox) }, enabled = wallbox.isBookable)
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.error != null -> Column(Modifier.padding(padding)) { ErrorBox(state.error, onRetry) }
            wallbox != null -> Column(
                modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                DetailHero(
                    modelBadge = Strings.modelBadge(wallbox.manufacturer, wallbox.model, wallbox.powerKw),
                    isFavorite = isFavorite,
                    onBack = onBack,
                    onToggleFavorite = onToggleFavorite,
                )
                PhotoStrip(wallbox)
                DetailContent(wallbox, state, favoriteError, onFavoriteErrorShown)
            }
        }
    }
}

/** Fotos des Hosts als waagerechte Leiste unter dem Kopf; ohne Fotos entfällt sie. */
@Composable
private fun PhotoStrip(wallbox: Wallbox) {
    if (wallbox.photos.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.Medium),
        contentPadding = PaddingValues(horizontal = Spacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        itemsIndexed(wallbox.photos, key = { _, path -> path }) { index, path ->
            PhotoImage(
                path = path,
                contentDescription = Strings.photoDescription(wallbox.name, index, wallbox.photos.size),
                modifier = Modifier.height(PHOTO_STRIP_HEIGHT).aspectRatio(PHOTO_ASPECT).clip(MaterialTheme.shapes.medium),
                thumbnail = false,
                placeholder = {
                    Box(Modifier.height(PHOTO_STRIP_HEIGHT).aspectRatio(PHOTO_ASPECT).background(ChargeShareColors.Surface2))
                },
            )
        }
    }
}

@Composable
private fun DetailContent(wallbox: Wallbox, state: WallboxDetailUiState, favoriteError: AppError?, onFavoriteErrorShown: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        favoriteError?.let { ErrorText(Strings.favoriteError(it), Modifier.clickable(onClick = onFavoriteErrorShown)) }
        TitleBlock(wallbox)
        InfoChips(wallbox)
        PriceBlock(wallbox)
        state.host?.let {
            SectionTitle(Strings.HostSection)
            HostCard(it)
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

/** Name, Adresse, Bewertung und Verfügbarkeits-Status. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleBlock(wallbox: Wallbox) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
        Text(wallbox.name, style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            Icon(AppIcons.Place, contentDescription = null, tint = ChargeShareColors.OnSurfaceMuted, modifier = Modifier.size(ADDRESS_ICON_SIZE))
            Text(wallbox.fullAddress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.XSmall),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (wallbox.ratingCount > 0) Icon(AppIcons.Star, contentDescription = null, tint = ChargeShareColors.Accent)
            Text(Strings.ratingSummary(wallbox.ratingAvg, wallbox.ratingCount), style = MaterialTheme.typography.bodyMedium)
            AvailabilityPill(wallbox.isOnline, wallbox.payoutsReady)
        }
    }
}

/** Eckdaten als Umriss-Chips: Leistung, Stecker, Öffnungszeit (falls an allen Tagen gleich) und Eichzähler. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoChips(wallbox: Wallbox) {
    val openingHours = remember(wallbox.availability) {
        wallbox.availability.utcToLocal(WALLBOX_ZONE, Clock.System.now()).commonWindow()?.let(::formatOpeningHours)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        OutlineChip(formatPowerKw(wallbox.powerKw), icon = AppIcons.Bolt)
        OutlineChip(wallbox.connectorType.displayName, icon = AppIcons.Plug)
        openingHours?.let { OutlineChip(it, icon = AppIcons.Calendar) }
        OutlineChip(if (wallbox.isMetered) Strings.Metered else Strings.NotMetered, icon = AppIcons.Shield)
    }
}

/** Großer Preis („0,34 €“ / kWh) und erlaubte Buchungsdauer. Ein €/km-Hinweis fehlt bewusst: er bräuchte einen angenommenen Verbrauch. */
@Composable
private fun PriceBlock(wallbox: Wallbox) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            Text(formatEuro(wallbox.pricePerKwh), style = MaterialTheme.typography.headlineMedium, color = ChargeShareColors.PrimaryText)
            Text(Strings.PerKwh, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.XSmall))
        }
        Text(Strings.duration(wallbox.minDurationMin, wallbox.maxDurationMin), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val ADDRESS_ICON_SIZE = 16.dp

@Composable
private fun AvailabilityCard(wallbox: Wallbox, freeSlotsToday: Int?) {
    SurfaceCard {
        SectionTitle(Strings.Availability)
        freeSlotsToday?.let { Text(Strings.freeSlotsToday(it), style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.PrimaryText) }
        if (wallbox.availability.isEmpty()) {
            Text(Strings.NoAvailability, style = MaterialTheme.typography.bodyMedium)
        } else {
            val localAvailability = remember(wallbox.availability) { wallbox.availability.utcToLocal(WALLBOX_ZONE, Clock.System.now()) }
            localAvailability.entries.sortedBy { weekdayIndexFromKey(it.key) }.forEach { (day, slots) ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                    Text(weekdayShortFromKey(day), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = Spacing.Small))
                    Text(slots.joinToString(", ") { "${it.start}–${it.end}" }, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(Strings.AvailabilityLocalNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private val PHOTO_STRIP_HEIGHT = 180.dp
private const val PHOTO_ASPECT = 4f / 3f
