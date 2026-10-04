package app.chargeshare.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Elevation
import app.chargeshare.ui.Spacing

/**
 * Filter-Pill im Design-Stil: vollrund, 1,5 dp Rand; gewählt tiefgrün mit weißer Schrift, sonst weiß mit grauer Schrift.
 * Ersetzt überall den Standard-[FilterChip] von Material.
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ChargeShareColors.Surface,
            labelColor = ChargeShareColors.OnSurfaceVariant,
            iconColor = ChargeShareColors.OnSurfaceVariant,
            selectedContainerColor = ChargeShareColors.Primary,
            selectedLabelColor = ChargeShareColors.OnPrimary,
            selectedLeadingIconColor = ChargeShareColors.OnPrimary,
        ),
        elevation = FilterChipDefaults.filterChipElevation(elevation = Elevation.Card),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = ChargeShareColors.Outline,
            selectedBorderColor = ChargeShareColors.Primary,
            borderWidth = CHIP_BORDER_WIDTH,
            selectedBorderWidth = CHIP_BORDER_WIDTH,
        ),
    )
}

private val CHIP_BORDER_WIDTH = Spacing.XSmall / 3

/** Waagerecht scrollbare Chip-Reihe, in der genau eine Option (oder keine) gewählt ist. */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.Medium),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        items(options) { option ->
            AppFilterChip(selected = option == selected, onClick = { onSelected(option) }, label = label(option))
        }
    }
}
