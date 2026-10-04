package app.chargeshare.ui.wallbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AppCard
import app.chargeshare.ui.components.FavoriteButton
import app.chargeshare.ui.components.InfoPill
import app.chargeshare.ui.formatDistance
import app.chargeshare.ui.formatPowerKw
import app.chargeshare.ui.formatPricePerKwh

/** Listeneintrag einer Wallbox (Name mit Herz, Adresse, Eckdaten, Preis). */
@Composable
fun WallboxCard(
    wallbox: Wallbox,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(wallbox.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FavoriteButton(isFavorite, onToggleFavorite, modifier = Modifier.size(FAVORITE_BUTTON_SIZE))
                }
                Text(wallbox.fullAddress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val facts = listOfNotNull(
                    formatPowerKw(wallbox.powerKw),
                    wallbox.connectorType.displayName,
                    wallbox.distanceM?.let(::formatDistance),
                ).joinToString(" · ")
                Text(facts, style = MaterialTheme.typography.bodyMedium)
                if (wallbox.ratingCount > 0) {
                    Text(Strings.ratingSummary(wallbox.ratingAvg, wallbox.ratingCount), style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Text(formatPricePerKwh(wallbox.pricePerKwh), style = MaterialTheme.typography.titleMedium, color = ChargeShareColors.PrimaryText)
                AvailabilityPill(wallbox.isOnline, wallbox.payoutsReady)
            }
        }
    }
}

/** Status immer auch als Text, nicht nur über die Farbe. */
@Composable
fun AvailabilityPill(isOnline: Boolean, payoutsReady: Boolean = true, modifier: Modifier = Modifier) {
    if (isOnline && payoutsReady) {
        InfoPill(Strings.Online, modifier)
    } else if (isOnline) {
        InfoPill(Strings.HostPayoutsPendingTitle, modifier, containerColor = ChargeShareColors.AccentLight, contentColor = ChargeShareColors.OnAccentLight)
    } else {
        InfoPill(Strings.Offline, modifier, containerColor = ChargeShareColors.SurfaceVariant, contentColor = ChargeShareColors.OnSurfaceVariant)
    }
}

private val FAVORITE_BUTTON_SIZE = 40.dp
