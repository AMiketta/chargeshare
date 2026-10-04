package app.chargeshare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing

/**
 * Untere Navigation im Design-Stil: weiße Leiste mit Trennlinie; das aktive Ziel hat ein grünes Icon
 * auf einer hellgrünen Pill, alle anderen sind grau.
 */
@Composable
fun <T> AppNavigationBar(
    items: List<T>,
    selected: T,
    title: (T) -> String,
    icon: (T) -> ImageVector,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(ChargeShareColors.Surface)) {
        HorizontalDivider(color = ChargeShareColors.Outline)
        Row(
            modifier = Modifier.navigationBarsPadding().fillMaxWidth().padding(vertical = Spacing.Small),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                NavigationBarEntry(
                    title = title(item),
                    icon = icon(item),
                    isSelected = item == selected,
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavigationBarEntry(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (isSelected) ChargeShareColors.PrimaryText else ChargeShareColors.OnSurfaceMuted
    Column(
        modifier = modifier
            .height(NAV_ENTRY_HEIGHT)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.XSmall, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .width(NAV_PILL_WIDTH)
                .height(NAV_PILL_HEIGHT)
                .clip(CircleShape)
                .background(if (isSelected) ChargeShareColors.PrimarySurface else ChargeShareColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(NAV_ICON_SIZE))
        }
        Text(title, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}

private val NAV_ENTRY_HEIGHT = 56.dp
private val NAV_PILL_WIDTH = 56.dp
private val NAV_PILL_HEIGHT = 30.dp
private val NAV_ICON_SIZE = 22.dp
