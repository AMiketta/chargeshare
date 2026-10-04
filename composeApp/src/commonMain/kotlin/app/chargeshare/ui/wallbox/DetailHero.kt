package app.chargeshare.ui.wallbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AppIconButton
import app.chargeshare.ui.components.FavoriteButton

/**
 * Kopfbereich der Detailseite im Design-Stil: tiefgrün, Zurück-Pfeil links, Herz rechts,
 * in der Mitte das Wallbox-Symbol mit dem amberfarbenen Modell-Badge ([modelBadge], z. B. „KEBA P30 · 11 kW“).
 */
@Composable
fun DetailHero(
    modelBadge: String,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ChargeShareColors.Primary)
            .statusBarsPadding()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(AppIcons.Back, Strings.Back, onBack, tint = ChargeShareColors.OnPrimary)
            Box(modifier = Modifier.weight(1f))
            FavoriteButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite,
                activeTint = ChargeShareColors.Accent,
                inactiveTint = ChargeShareColors.OnPrimary,
            )
        }
        Box(
            modifier = Modifier.size(HERO_ICON_BOX).clip(RoundedCornerShape(HERO_ICON_RADIUS)).background(ChargeShareColors.OnPrimary.copy(alpha = HERO_ICON_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Bolt, contentDescription = null, tint = ChargeShareColors.OnPrimary, modifier = Modifier.size(HERO_ICON_SIZE))
        }
        Surface(shape = CircleShape, color = ChargeShareColors.Accent, contentColor = ChargeShareColors.OnAccent, modifier = Modifier.padding(bottom = Spacing.Small)) {
            Text(
                modelBadge,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.XSmall + Spacing.XSmall / 2),
            )
        }
    }
}

private val HERO_ICON_BOX = 64.dp
private val HERO_ICON_SIZE = 32.dp
private val HERO_ICON_RADIUS = 20.dp
private const val HERO_ICON_ALPHA = 0.2f
