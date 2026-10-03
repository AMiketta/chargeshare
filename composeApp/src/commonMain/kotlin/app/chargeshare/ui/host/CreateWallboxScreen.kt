package app.chargeshare.ui.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.chargeshare.presentation.host.CreateWallboxUiState
import app.chargeshare.presentation.host.CreateWallboxViewModel
import app.chargeshare.presentation.host.WEEKDAY_KEYS
import app.chargeshare.presentation.host.WallboxField
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AmberButton
import app.chargeshare.ui.components.ChoiceChips
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.FormTextField
import app.chargeshare.ui.components.HeroHeader
import app.chargeshare.ui.components.SectionTitle
import app.chargeshare.ui.weekdayShortFromKey

@Composable
fun CreateWallboxRoute(
    viewModel: CreateWallboxViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    // Nach dem Anlegen zurück zum Dashboard, das sich beim Einblenden neu lädt.
    LaunchedEffect(state.created) { if (state.created) onBack() }
    CreateWallboxScreen(state, viewModel::onChange, viewModel::onDayToggled, viewModel::onSubmit, onBack, modifier)
}

/** Formular zum Anlegen einer Wallbox. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateWallboxScreen(
    state: CreateWallboxUiState,
    onChange: ((CreateWallboxUiState) -> CreateWallboxUiState) -> Unit,
    onDayToggled: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun error(field: WallboxField) = if (field in state.errors) Strings.wallboxFieldError(field) else null
    Scaffold(
        modifier = modifier,
        topBar = { HeroHeader(title = Strings.CreateWallboxTitle, onBack = onBack) },
        bottomBar = {
            Surface(shadowElevation = Spacing.Small) {
                Column(modifier = Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    state.submitError?.let { ErrorText(Strings.errorMessage(it)) }
                    AmberButton(Strings.Save, onSubmit, isLoading = state.isSubmitting)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            FormTextField(state.name, { v -> onChange { it.copy(name = v) } }, Strings.FieldWallboxName, errorText = error(WallboxField.Name))
            FormTextField(state.address, { v -> onChange { it.copy(address = v) } }, Strings.FieldAddress, errorText = error(WallboxField.Address))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                FormTextField(state.postalCode, { v -> onChange { it.copy(postalCode = v) } }, Strings.FieldPostalCode, Modifier.weight(1f), error(WallboxField.PostalCode), keyboardType = KeyboardType.Number)
                FormTextField(state.city, { v -> onChange { it.copy(city = v) } }, Strings.FieldCity, Modifier.weight(2f), error(WallboxField.City))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                FormTextField(state.lat, { v -> onChange { it.copy(lat = v) } }, Strings.FieldLat, Modifier.weight(1f), error(WallboxField.Lat), keyboardType = KeyboardType.Decimal)
                FormTextField(state.lng, { v -> onChange { it.copy(lng = v) } }, Strings.FieldLng, Modifier.weight(1f), error(WallboxField.Lng), keyboardType = KeyboardType.Decimal)
            }
            Text(Strings.CoordinatesHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                FormTextField(state.powerKw, { v -> onChange { it.copy(powerKw = v) } }, Strings.FieldPowerKw, Modifier.weight(1f), error(WallboxField.PowerKw), keyboardType = KeyboardType.Decimal)
                FormTextField(state.price, { v -> onChange { it.copy(price = v) } }, Strings.FieldPrice, Modifier.weight(1f), error(WallboxField.Price), keyboardType = KeyboardType.Decimal)
            }
            SectionTitle(Strings.Connector)
            ChoiceChips(ConnectorType.entries, state.connector, { it.displayName }, { c -> onChange { it.copy(connector = c) } }, contentPadding = PaddingValues())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Switch(state.isMetered, { v -> onChange { it.copy(isMetered = v) } })
                Text(Strings.FieldMetered, style = MaterialTheme.typography.bodyLarge)
            }
            Text(Strings.MeteredHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle(Strings.Availability)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                FormTextField(state.availabilityStart, { v -> onChange { it.copy(availabilityStart = v) } }, Strings.FieldAvailabilityStart, Modifier.weight(1f), error(WallboxField.AvailabilityStart))
                FormTextField(state.availabilityEnd, { v -> onChange { it.copy(availabilityEnd = v) } }, Strings.FieldAvailabilityEnd, Modifier.weight(1f), error(WallboxField.AvailabilityEnd))
            }
            Text(Strings.AvailabilityUtcHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Strings.FieldDays, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                WEEKDAY_KEYS.forEach { key ->
                    FilterChip(selected = key in state.days, onClick = { onDayToggled(key) }, label = { Text(weekdayShortFromKey(key)) })
                }
            }
            error(WallboxField.Days)?.let { ErrorText(it) }
        }
    }
}
