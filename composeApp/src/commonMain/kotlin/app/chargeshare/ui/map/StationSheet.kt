package app.chargeshare.ui.map

import app.chargeshare.ui.LocalTabBarOverlap
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Elevation
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.PhotoImage
import app.chargeshare.ui.formatDistance
import app.chargeshare.ui.formatEuro
import app.chargeshare.ui.formatPowerKw

/**
 * Fenster am unteren Kartenrand: „N Stationen in der Nähe“ mit Leistung, Stecker, Entfernung und Preis je Wallbox.
 * Die aktive Wallbox (Pin) ist hervorgehoben und wird in die Liste gescrollt; Tipp auf eine Zeile öffnet das Detail.
 */
@Composable
fun StationSheet(
    wallboxes: List<Wallbox>,
    activeId: String?,
    onOpen: (Wallbox) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (wallboxes.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(activeId, wallboxes) {
        val index = wallboxes.indexOfFirst { it.id == activeId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER),
        color = ChargeShareColors.Surface,
        shadowElevation = Elevation.Raised,
    ) {
        Column(modifier = Modifier.padding(top = Spacing.Small), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = HANDLE_WIDTH, height = HANDLE_HEIGHT)
                    .clip(CircleShape)
                    .background(ChargeShareColors.Outline),
            )
            Text(
                Strings.stationsNearby(wallboxes.size).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = ChargeShareColors.OnSurfaceMuted,
                modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.XSmall),
            )
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = SHEET_LIST_MAX_HEIGHT)) {
                items(wallboxes, key = { it.id }) { wallbox ->
                    StationRow(wallbox, isActive = wallbox.id == activeId, onClick = { onOpen(wallbox) })
                }
            }
            // Fläche läuft unter einer darüberliegenden Tab-Leiste (iOS) durch; Inhalt endet darüber.
            Spacer(Modifier.height(LocalTabBarOverlap.current))
        }
    }
}

@Composable
private fun StationRow(wallbox: Wallbox, isActive: Boolean, onClick: () -> Unit) {
    val facts = listOfNotNull(
        formatPowerKw(wallbox.powerKw),
        wallbox.connectorType.displayName,
        wallbox.distanceM?.let(::formatDistance),
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) ChargeShareColors.PrimarySurface else ChargeShareColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small + Spacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium - Spacing.XSmall),
    ) {
        Box(
            modifier = Modifier.size(ROW_ICON_BOX).clip(MaterialTheme.shapes.small).background(ChargeShareColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Bolt, contentDescription = null, tint = ChargeShareColors.OnPrimary, modifier = Modifier.size(ROW_ICON_SIZE))
            // Das Foto liegt über dem Blitz-Platzhalter und ersetzt ihn, sobald es geladen ist.
            wallbox.photos.firstOrNull()?.let { path ->
                PhotoImage(path, contentDescription = null, modifier = Modifier.size(ROW_ICON_BOX))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(wallbox.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(facts, style = MaterialTheme.typography.bodySmall, color = ChargeShareColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatEuro(wallbox.pricePerKwh), style = MaterialTheme.typography.titleSmall, color = ChargeShareColors.PrimaryText)
    }
}

private val SHEET_CORNER = 26.dp
private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 4.dp
private val SHEET_LIST_MAX_HEIGHT = 200.dp
private val ROW_ICON_BOX = 36.dp
private val ROW_ICON_SIZE = 20.dp
