package app.chargeshare.ui.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.formatDate
import app.chargeshare.ui.formatTime
import app.chargeshare.ui.weekdayShort
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Anzahl Spalten des Startzeit-Rasters (Design: 3 × n). */
internal const val TIME_GRID_COLUMNS = 3

private val PickerBorderWidth = 1.5.dp
private val DatePillMinWidth = 52.dp
private val TimeCellHeight = 40.dp
private val DurationPillMinWidth = 72.dp

/** Abschnitts-Label in Versalien wie im Design (`.section-label`). */
@Composable
internal fun BookingSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = ChargeShareColors.OnSurfaceMuted,
        modifier = modifier,
    )
}

/** Horizontaler Datumsscroller: Wochentag über Tageszahl, gewählter Tag grün. */
@Composable
internal fun DateScroller(
    days: List<LocalDate>,
    selected: LocalDate,
    onSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.Medium - Spacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        items(days) { day ->
            val isSelected = day == selected
            Surface(
                modifier = Modifier
                    .defaultMinSize(minWidth = DatePillMinWidth)
                    .semantics { contentDescription = formatDate(day) }
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(day) }),
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) ChargeShareColors.Primary else ChargeShareColors.Surface,
                contentColor = if (isSelected) ChargeShareColors.OnPrimary else ChargeShareColors.OnSurface,
                border = BorderStroke(PickerBorderWidth, if (isSelected) ChargeShareColors.Primary else ChargeShareColors.Outline),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.Medium - Spacing.XSmall, vertical = Spacing.Small),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(weekdayShort(day.dayOfWeek), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(day.day.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * Startzeiten als Raster mit [TIME_GRID_COLUMNS] Spalten. Freie Zeiten sind wählbar, [lockedStarts] (belegt) erscheinen
 * grau und durchgestrichen und sind nicht wählbar.
 */
@Composable
internal fun StartTimeGrid(
    freeStarts: List<Instant>,
    lockedStarts: List<Instant>,
    selected: Instant?,
    zone: TimeZone,
    onSelected: (Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = (freeStarts.map { it to false } + lockedStarts.filter { it !in freeStarts }.map { it to true }).sortedBy { it.first }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Small - Spacing.XSmall / 2)) {
        cells.chunked(TIME_GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small - Spacing.XSmall / 2)) {
                row.forEach { (start, isLocked) ->
                    TimeCell(
                        text = formatTime(start, zone),
                        selected = start == selected,
                        locked = isLocked,
                        onClick = { onSelected(start) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(TIME_GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TimeCell(text: String, selected: Boolean, locked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container = when {
        locked -> ChargeShareColors.SurfaceVariant
        selected -> ChargeShareColors.PrimarySurface
        else -> ChargeShareColors.Surface
    }
    val content = when {
        locked -> ChargeShareColors.OnSurfaceMuted
        selected -> ChargeShareColors.Primary
        else -> ChargeShareColors.OnSurfaceVariant
    }
    Surface(
        modifier = modifier
            .height(TimeCellHeight)
            .semantics { if (locked) stateDescription = Strings.SlotTaken }
            .selectable(selected = selected, enabled = !locked, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        border = BorderStroke(PickerBorderWidth, if (selected) ChargeShareColors.Primary else ChargeShareColors.Outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                textDecoration = if (locked) TextDecoration.LineThrough else null,
            )
        }
    }
}

/** Dauer-Auswahl: gewählte Dauer in Amber (wie im Design), waagerecht scrollbar. */
@Composable
internal fun DurationPills(
    options: List<Int>,
    selected: Int?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.Small - Spacing.XSmall / 2)) {
        items(options) { minutes ->
            val isSelected = minutes == selected
            Surface(
                modifier = Modifier
                    .defaultMinSize(minWidth = DurationPillMinWidth)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(minutes) }),
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) ChargeShareColors.AccentLight else ChargeShareColors.Surface,
                contentColor = if (isSelected) ChargeShareColors.AccentDark else ChargeShareColors.OnSurfaceVariant,
                border = BorderStroke(PickerBorderWidth, if (isSelected) ChargeShareColors.Accent else ChargeShareColors.Outline),
            ) {
                Box(modifier = Modifier.padding(Spacing.Small), contentAlignment = Alignment.Center) {
                    Text(Strings.durationLabel(minutes), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
