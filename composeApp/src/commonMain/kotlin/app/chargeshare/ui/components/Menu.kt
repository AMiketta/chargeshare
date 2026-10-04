package app.chargeshare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing

/** Farbpaare (Icon-Hintergrund, Icon) der Menü-Einträge aus dem Design. */
enum class MenuIconStyle(val background: Color, val tint: Color) {
    Green(ChargeShareColors.PrimarySurface, ChargeShareColors.Primary),
    Amber(ChargeShareColors.AccentLight, ChargeShareColors.AccentDark),
    Neutral(ChargeShareColors.Background, ChargeShareColors.OnSurfaceVariant),
    Danger(ChargeShareColors.ErrorLight, ChargeShareColors.Error),
}

/**
 * Gruppierte Liste im Stil von iOS-Einstellungen: optionale Überschrift in Großbuchstaben über einer weißen Karte,
 * die [MenuItem]s mit Trennlinien enthält.
 */
@Composable
fun MenuGroup(
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        if (label != null) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = ChargeShareColors.OnSurfaceMuted,
                modifier = Modifier.padding(horizontal = Spacing.Small),
            )
        }
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

/** Trennlinie zwischen zwei [MenuItem]s (nicht nach dem letzten setzen). */
@Composable
fun MenuDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = MENU_DIVIDER_INSET), color = ChargeShareColors.Outline)
}

/**
 * Eintrag einer [MenuGroup]: farbiges Icon, Titel, optionale Zeile darunter und Pfeil.
 * Die ganze Zeile ist klickbar (mindestens 48 dp hoch).
 */
@Composable
fun MenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    style: MenuIconStyle = MenuIconStyle.Neutral,
    titleColor: Color = ChargeShareColors.OnSurface,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.MinTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small + Spacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium - Spacing.XSmall),
    ) {
        Box(
            modifier = Modifier.size(MENU_ICON_BOX).clip(RoundedCornerShape(MENU_ICON_CORNER)).background(style.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = style.tint, modifier = Modifier.size(MENU_ICON_SIZE))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ChargeShareColors.OnSurfaceMuted)
            }
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = ChargeShareColors.OnSurfaceMuted)
    }
}

private val MENU_ICON_BOX = 34.dp
private val MENU_ICON_SIZE = 18.dp
private val MENU_ICON_CORNER = 10.dp
private val MENU_DIVIDER_INSET = 62.dp
