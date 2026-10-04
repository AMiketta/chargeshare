package app.chargeshare.ui.host

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.chargeshare.presentation.host.DayPart
import app.chargeshare.presentation.host.dayPartFor
import app.chargeshare.shared.model.HostEarnings
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.formatDecimal
import app.chargeshare.ui.formatEuro
import app.chargeshare.ui.formatKwhShort
import app.chargeshare.ui.monthName
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

/** Tiefgrüner Kopf des Host-Dashboards: Begrüßung, Name, Status und Einnahmen-Karte (wie im Design-Mockup). */
@Composable
fun HostDashboardHeader(
    userName: String,
    subtitle: String,
    now: LocalDateTime?,
    earnings: HostEarnings?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ChargeShareColors.Primary)
            .statusBarsPadding()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium),
    ) {
        Text(greeting(now), style = MaterialTheme.typography.labelLarge, color = ChargeShareColors.OnPrimary.copy(alpha = MUTED_ALPHA))
        Text(userName, style = MaterialTheme.typography.headlineSmall, color = ChargeShareColors.OnPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ChargeShareColors.OnPrimary.copy(alpha = MUTED_ALPHA))
        if (earnings != null) {
            EarningsCard(earnings, now, Modifier.padding(top = Spacing.Medium))
        }
    }
}

private fun greeting(now: LocalDateTime?): String = when (now?.let { dayPartFor(it.hour) }) {
    DayPart.MORNING -> Strings.GreetingMorning
    DayPart.DAY -> Strings.GreetingDay
    DayPart.EVENING -> Strings.GreetingEvening
    null -> Strings.GreetingFallback
}

@Composable
private fun EarningsCard(earnings: HostEarnings, now: LocalDateTime?, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = CARD_ALPHA))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = BORDER_ALPHA)), shape)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.XSmall),
    ) {
        if (now != null) {
            Text(
                Strings.earningsLabel(monthName(now.month), now.year),
                style = MaterialTheme.typography.labelMedium,
                color = ChargeShareColors.OnPrimary.copy(alpha = MUTED_ALPHA),
            )
        } else {
            Text(Strings.EarningsMonth, style = MaterialTheme.typography.labelMedium, color = ChargeShareColors.OnPrimary.copy(alpha = MUTED_ALPHA))
        }
        Text(formatEuro(earnings.month), style = MaterialTheme.typography.displaySmall, color = ChargeShareColors.OnPrimary)
        val change = earnings.monthChangePct
        if (change != null && now != null) {
            val previous = monthName(now.date.minus(1, DateTimeUnit.MONTH).month)
            DeltaPill(Strings.earningsDelta(change, previous))
        }
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = Spacing.Small)) {
            Stat(earnings.monthSessions.toString(), Strings.Sessions, first = true)
            Stat(formatKwhShort(earnings.monthKwh), Strings.StatKwhDelivered)
            Stat(if (earnings.avgRating > 0) "${formatDecimal(earnings.avgRating, 1)} ★" else "–", Strings.AverageRating)
        }
    }
}

@Composable
private fun DeltaPill(text: String) {
    Text(
        text,
        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = BORDER_ALPHA)).padding(horizontal = Spacing.Small, vertical = Spacing.XSmall),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = ChargeShareColors.OnPrimary,
    )
}

@Composable
private fun RowScope.Stat(value: String, label: String, first: Boolean = false) {
    Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
        if (!first) {
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = BORDER_ALPHA)))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.XSmall), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = ChargeShareColors.OnPrimary, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ChargeShareColors.OnPrimary.copy(alpha = MUTED_ALPHA), textAlign = TextAlign.Center)
        }
    }
}

private const val MUTED_ALPHA = 0.75f
private const val CARD_ALPHA = 0.12f
private const val BORDER_ALPHA = 0.18f
