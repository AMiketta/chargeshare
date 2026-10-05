package app.chargeshare.ui.wallbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.chargeshare.data.WallboxFilter
import app.chargeshare.presentation.map.toPin
import app.chargeshare.presentation.wallbox.FavoritesUiState
import app.chargeshare.presentation.wallbox.FavoritesViewModel
import app.chargeshare.presentation.wallbox.SearchViewMode
import app.chargeshare.presentation.wallbox.WallboxListUiState
import app.chargeshare.presentation.wallbox.WallboxListViewModel
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AppCard
import app.chargeshare.ui.components.AppFilterChip
import app.chargeshare.ui.components.ChoiceChips
import app.chargeshare.ui.components.SearchEmptyBox
import app.chargeshare.ui.components.ErrorBox
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.HeroHeader
import app.chargeshare.ui.components.LoadingBox
import app.chargeshare.ui.components.SectionTitle
import app.chargeshare.ui.map.StationSheet
import app.chargeshare.ui.map.WallboxMap
import androidx.compose.ui.unit.dp
import ovh.plrapps.mapcompose.core.TileStreamProvider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

private val RADIUS_OPTIONS_M = listOf(1_000.0, 3_000.0, 5_000.0, 10_000.0, 25_000.0)
private val POWER_OPTIONS_KW = listOf(7.4, 11.0, 22.0)
private val PRICE_OPTIONS_EUR = listOf(0.30, 0.40, 0.50)

@Composable
fun WallboxListRoute(
    viewModel: WallboxListViewModel,
    favorites: FavoritesViewModel,
    tileSource: TileStreamProvider,
    onOpenWallbox: (Wallbox) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val favoritesState by favorites.state.collectAsState()
    WallboxListScreen(
        state = state,
        favorites = favoritesState,
        onToggleFavorite = favorites::onToggle,
        onFavoriteErrorShown = favorites::onErrorShown,
        onQueryChange = viewModel::onQueryChange,
        onRadiusChange = viewModel::onRadiusChange,
        onConnectorChange = viewModel::onConnectorChange,
        onMinPowerChange = viewModel::onMinPowerChange,
        onMaxPriceChange = viewModel::onMaxPriceChange,
        onOnlyOnlineChange = viewModel::onOnlyOnlineChange,
        onClearQuickFilters = viewModel::onClearQuickFilters,
        onWallboxSelected = viewModel::onWallboxSelected,
        onViewModeChange = viewModel::onViewModeChange,
        onRetry = viewModel::onRefresh,
        onAllowLocation = viewModel::onAllowLocation,
        onSkipLocation = viewModel::onSkipLocation,
        onDismissLocationPrompt = viewModel::onDismissLocationPrompt,
        onOpenWallbox = onOpenWallbox,
        tileSource = tileSource,
        modifier = modifier,
    )
}

/**
 * Suche mit Umschalter Karte/Liste. Wo die Plattform keine Karte zeigt ([WallboxListUiState.mapSupported] = `false`),
 * gibt es nur die Liste mit einem Hinweis.
 */
@Composable
fun WallboxListScreen(
    state: WallboxListUiState,
    favorites: FavoritesUiState,
    onToggleFavorite: (String) -> Unit,
    onFavoriteErrorShown: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRadiusChange: (Double) -> Unit,
    onConnectorChange: (ConnectorType?) -> Unit,
    onMinPowerChange: (Double?) -> Unit,
    onMaxPriceChange: (Double?) -> Unit,
    onOnlyOnlineChange: (Boolean) -> Unit,
    onClearQuickFilters: () -> Unit,
    onWallboxSelected: (String) -> Unit,
    onViewModeChange: (SearchViewMode) -> Unit,
    onRetry: () -> Unit,
    onAllowLocation: () -> Unit,
    onSkipLocation: () -> Unit,
    onDismissLocationPrompt: () -> Unit,
    onOpenWallbox: (Wallbox) -> Unit,
    tileSource: TileStreamProvider,
    modifier: Modifier = Modifier,
) {
    // Rein visueller Zustand: ob die erweiterten Filter (Umkreis, alle Stecker, Leistung, Preis) aufgeklappt sind.
    var showMoreFilters by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        HeroHeader(
            title = Strings.AppName,
            subtitle = Strings.searchCenterNote(state.usesDeviceLocation, state.showLocationFallbackHint),
        ) {
            SearchField(state.query, onQueryChange)
        }
        if (state.mapSupported) {
            ViewModeSwitch(state.viewMode, onViewModeChange, Modifier.padding(top = Spacing.Small))
        }
        QuickFilters(
            filter = state.filter,
            showMore = showMoreFilters,
            onShowMoreChange = { showMoreFilters = it },
            onConnectorChange = onConnectorChange,
            onMinPowerChange = onMinPowerChange,
            onOnlyOnlineChange = onOnlyOnlineChange,
            onClearQuickFilters = onClearQuickFilters,
            showUseNearby = state.showLocationFallbackHint && !state.usesDeviceLocation,
            onUseNearby = onAllowLocation,
            modifier = Modifier.padding(vertical = Spacing.Small),
        )
        if (showMoreFilters) {
            Column(Modifier.heightIn(max = MORE_FILTERS_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
                MoreFilters(state.filter, onRadiusChange, onConnectorChange, onMinPowerChange, onMaxPriceChange)
            }
        }
        if (state.showMap) {
            MapResults(state, onRetry, onOpenWallbox, onWallboxSelected, tileSource)
        } else {
            ListResults(state, favorites, onToggleFavorite, onFavoriteErrorShown, onRetry, onOpenWallbox)
        }
    }
    if (state.showLocationPrompt) {
        LocationPermissionDialog(
            onAllow = onAllowLocation,
            onSkip = onSkipLocation,
            onDismiss = onDismissLocationPrompt,
        )
    }
}

@Composable
private fun LocationPermissionDialog(
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.LocationPromptTitle) },
        text = { Text(Strings.LocationPromptBody) },
        confirmButton = { TextButton(onClick = onAllow) { Text(Strings.LocationPromptAllow) } },
        dismissButton = { TextButton(onClick = onSkip) { Text(Strings.LocationPromptSkip) } },
    )
}

private val MORE_FILTERS_MAX_HEIGHT = 220.dp

@Composable
private fun ListResults(
    state: WallboxListUiState,
    favorites: FavoritesUiState,
    onToggleFavorite: (String) -> Unit,
    onFavoriteErrorShown: () -> Unit,
    onRetry: () -> Unit,
    onOpenWallbox: (Wallbox) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        favorites.error?.let { error ->
            item(key = "favoriteError") {
                ErrorText(Strings.favoriteError(error), Modifier.clickable(onClick = onFavoriteErrorShown).padding(horizontal = Spacing.Medium))
            }
        }
        if (!state.mapSupported) {
            item(key = "map") { MapUnavailableNote(Modifier.padding(horizontal = Spacing.Medium)) }
        }
        when {
            state.isLoading -> item(key = "loading") { LoadingBox(Modifier.padding(Spacing.XLarge)) }
            state.error != null -> item(key = "error") { ErrorBox(state.error, onRetry) }
            state.visibleWallboxes.isEmpty() -> item(key = "empty") { SearchEmptyBox() }
            else -> {
                item(key = "count") {
                    SectionTitle(Strings.stationsNearby(state.visibleWallboxes.size), Modifier.padding(horizontal = Spacing.Medium))
                }
                items(state.visibleWallboxes, key = { it.id }) { wallbox ->
                    WallboxCard(
                        wallbox = wallbox,
                        isFavorite = favorites.isFavorite(wallbox.id),
                        onClick = { onOpenWallbox(wallbox) },
                        onToggleFavorite = { onToggleFavorite(wallbox.id) },
                        modifier = Modifier.padding(horizontal = Spacing.Medium),
                    )
                }
            }
        }
    }
}

@Composable
private fun MapResults(
    state: WallboxListUiState,
    onRetry: () -> Unit,
    onOpenWallbox: (Wallbox) -> Unit,
    onWallboxSelected: (String) -> Unit,
    tileSource: TileStreamProvider,
) {
    val wallboxes = state.visibleWallboxes
    val pins = remember(wallboxes) { wallboxes.map(Wallbox::toPin) }
    val activeId = state.activeWallbox?.id
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { ErrorBox(it, onRetry) }
        if (!state.isLoading && state.error == null && wallboxes.isEmpty()) SearchEmptyBox()
        WallboxMap(
            pins = pins,
            tileSource = tileSource,
            onPinClick = onWallboxSelected,
            modifier = Modifier.fillMaxWidth().weight(1f),
            activePinId = activeId,
            center = state.center,
        ) {
            StationSheet(wallboxes, activeId, onOpenWallbox, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Umschalter Karte/Liste. */
@Composable
private fun ViewModeSwitch(
    viewMode: SearchViewMode,
    onViewModeChange: (SearchViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = Spacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppFilterChip(
            selected = viewMode == SearchViewMode.Map,
            onClick = { onViewModeChange(SearchViewMode.Map) },
            label = Strings.ViewMap,
            leadingIcon = AppIcons.Map,
        )
        AppFilterChip(
            selected = viewMode == SearchViewMode.List,
            onClick = { onViewModeChange(SearchViewMode.List) },
            label = Strings.ViewList,
            leadingIcon = AppIcons.List,
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(Strings.SearchPlaceholder) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        shape = RoundedCornerShape(SEARCH_FIELD_CORNER_DP),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ChargeShareColors.Surface,
            unfocusedContainerColor = ChargeShareColors.Surface,
        ),
    )
}

private val SEARCH_FIELD_CORNER_DP = Spacing.Large

/**
 * Schnellfilter-Pills wie im Design: „Alle“, „Typ 2“, „11 kW+“, „Jetzt frei“ und „Mehr Filter“.
 * „Alle“ ist gewählt, solange kein Schnellfilter aktiv ist, und setzt sie sonst zurück.
 */
@Composable
private fun QuickFilters(
    filter: WallboxFilter,
    showMore: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    onConnectorChange: (ConnectorType?) -> Unit,
    onMinPowerChange: (Double?) -> Unit,
    onOnlyOnlineChange: (Boolean) -> Unit,
    onClearQuickFilters: () -> Unit,
    showUseNearby: Boolean,
    onUseNearby: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noQuickFilter = filter.connector == null && filter.minPowerKw == null && !filter.onlyOnline
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        if (showUseNearby) {
            item {
                AppFilterChip(
                    selected = false,
                    onClick = onUseNearby,
                    label = Strings.UseNearby,
                    leadingIcon = AppIcons.Map,
                )
            }
        }
        item { AppFilterChip(selected = noQuickFilter, onClick = onClearQuickFilters, label = Strings.FilterAll) }
        item {
            AppFilterChip(
                selected = filter.connector == QUICK_CONNECTOR,
                onClick = { onConnectorChange(if (filter.connector == QUICK_CONNECTOR) null else QUICK_CONNECTOR) },
                label = QUICK_CONNECTOR.displayName,
            )
        }
        item {
            AppFilterChip(
                selected = filter.minPowerKw == QUICK_POWER_KW,
                onClick = { onMinPowerChange(if (filter.minPowerKw == QUICK_POWER_KW) null else QUICK_POWER_KW) },
                label = Strings.powerFilter(QUICK_POWER_KW),
            )
        }
        item {
            AppFilterChip(
                selected = filter.onlyOnline,
                onClick = { onOnlyOnlineChange(!filter.onlyOnline) },
                label = Strings.FilterOnlyOnline,
            )
        }
        item {
            AppFilterChip(
                selected = showMore || filter.hasExtraFilter,
                onClick = { onShowMoreChange(!showMore) },
                label = if (showMore) Strings.FewerFilters else Strings.MoreFilters,
            )
        }
    }
}

private val QUICK_CONNECTOR = ConnectorType.TYPE2
private const val QUICK_POWER_KW = 11.0

/** Gilt ein Umkreis- oder Preisfilter abweichend vom Standard bzw. eine Mindestleistung außerhalb der Schnellfilter? */
private val WallboxFilter.hasExtraFilter: Boolean
    get() = radiusM != WallboxFilter.DEFAULT_RADIUS_M || maxPricePerKwh != null ||
        (minPowerKw != null && minPowerKw != QUICK_POWER_KW) || (connector != null && connector != QUICK_CONNECTOR)

/** Erweiterte Filter: Umkreis, alle Steckertypen, Mindestleistung, Höchstpreis. */
@Composable
private fun MoreFilters(
    filter: WallboxFilter,
    onRadiusChange: (Double) -> Unit,
    onConnectorChange: (ConnectorType?) -> Unit,
    onMinPowerChange: (Double?) -> Unit,
    onMaxPriceChange: (Double?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small), modifier = Modifier.padding(bottom = Spacing.Small)) {
        ChoiceChips(RADIUS_OPTIONS_M, filter.radiusM, Strings::radiusLabel, onRadiusChange)
        ChoiceChips(
            options = listOf<ConnectorType?>(null) + ConnectorType.entries,
            selected = filter.connector,
            label = { it?.displayName ?: Strings.FilterAllConnectors },
            onSelected = onConnectorChange,
        )
        ChoiceChips(
            options = listOf<Double?>(null) + POWER_OPTIONS_KW,
            selected = filter.minPowerKw,
            label = { it?.let(Strings::powerFilter) ?: Strings.FilterAnyPower },
            onSelected = onMinPowerChange,
        )
        ChoiceChips(
            options = listOf<Double?>(null) + PRICE_OPTIONS_EUR,
            selected = filter.maxPricePerKwh,
            label = { it?.let(Strings::priceFilter) ?: Strings.FilterAnyPrice },
            onSelected = onMaxPriceChange,
        )
    }
}

@Composable
private fun MapUnavailableNote(modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = ChargeShareColors.PrimarySurface,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Icon(AppIcons.Map, contentDescription = null, tint = ChargeShareColors.PrimaryText)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                Text(Strings.MapUnavailableTitle, style = MaterialTheme.typography.titleSmall, color = ChargeShareColors.PrimaryText)
                Text(Strings.MapUnavailableBody, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
