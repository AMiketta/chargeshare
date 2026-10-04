package app.chargeshare.ui.wallbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.chargeshare.shared.model.HostProfile
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.InfoPill
import app.chargeshare.ui.components.SurfaceCard
import app.chargeshare.ui.initialsOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Host-Karte der Detailseite: Initialen-Avatar, gekürzter Name, „Superhost“, Mitglied seit und Sessions. */
@Composable
fun HostCard(
    host: HostProfile,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    SurfaceCard(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = Strings.hostDescription(host.name, host.isSuperhost) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape).background(ChargeShareColors.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(initialsOf(host.name), style = MaterialTheme.typography.titleMedium, color = ChargeShareColors.OnPrimary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
                    Text(host.name, style = MaterialTheme.typography.titleMedium)
                    if (host.isSuperhost) InfoPill(Strings.Superhost)
                }
                Text(
                    Strings.hostSummary(host.memberSince.toLocalDateTime(zone).year, host.sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val AVATAR_SIZE = 44.dp
