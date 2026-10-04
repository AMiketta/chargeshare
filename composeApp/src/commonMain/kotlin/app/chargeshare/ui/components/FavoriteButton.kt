package app.chargeshare.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings

/**
 * Herz zum Merken einer Wallbox. Gemerkt = gefülltes Herz in [activeTint], sonst Umriss in [inactiveTint].
 * Der Zustand steckt zusätzlich in der Beschreibung für Screenreader, nicht nur in der Form.
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    activeTint: Color = ChargeShareColors.Error,
    inactiveTint: Color = ChargeShareColors.OnSurfaceMuted,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.defaultMinSize(Spacing.MinTouchTarget, Spacing.MinTouchTarget).semantics { role = Role.Button },
    ) {
        Icon(
            imageVector = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
            contentDescription = if (isFavorite) Strings.RemoveFavorite else Strings.AddFavorite,
            tint = if (isFavorite) activeTint else inactiveTint,
        )
    }
}
