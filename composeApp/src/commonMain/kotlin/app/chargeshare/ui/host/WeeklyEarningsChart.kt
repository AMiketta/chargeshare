package app.chargeshare.ui.host

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.chargeshare.presentation.host.barFractions
import app.chargeshare.presentation.host.todayIndex
import app.chargeshare.shared.model.DailyEarnings
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AppCard
import app.chargeshare.ui.weekdayShort
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Karte „Wochenübersicht“: Balken der Tageseinnahmen (Montag bis Sonntag), heute hervorgehoben.
 * Gezeichnet mit dem Compose-Canvas, daher auf allen Plattformen gleich.
 */
@Composable
fun WeeklyEarningsCard(week: List<DailyEarnings>, today: LocalDate?, modifier: Modifier = Modifier) {
    val labels = week.indices.map { weekdayShort(DayOfWeek.entries[it % DayOfWeek.entries.size]) }
    val description = Strings.weekChartDescription(labels.zip(week.map { it.amount }))
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            Text(Strings.WeekOverview, style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.clearAndSetSemantics { contentDescription = description }) {
                WeekBars(week, highlighted = todayIndex(week, today))
                BarLabels(labels, highlighted = todayIndex(week, today))
            }
        }
    }
}

@Composable
private fun WeekBars(week: List<DailyEarnings>, highlighted: Int) {
    val fractions = barFractions(week.map { it.amount })
    val normal = ChargeShareColors.PrimaryLight
    val strong = ChargeShareColors.PrimaryText
    Canvas(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        if (fractions.isEmpty()) return@Canvas
        val gap = GAP.toPx()
        val barWidth = (size.width - gap * (fractions.size - 1)) / fractions.size
        val radius = CornerRadius(CORNER.toPx())
        fractions.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            val rect = RoundRect(
                left = index * (barWidth + gap),
                top = size.height - barHeight,
                right = index * (barWidth + gap) + barWidth,
                bottom = size.height,
                topLeftCornerRadius = radius,
                topRightCornerRadius = radius,
            )
            drawPath(Path().apply { addRoundRect(rect) }, color = if (index == highlighted) strong else normal)
        }
    }
}

@Composable
private fun BarLabels(labels: List<String>, highlighted: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.XSmall), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        labels.forEachIndexed { index, label ->
            val isToday = index == highlighted
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isToday) ChargeShareColors.PrimaryText else ChargeShareColors.OnSurfaceMuted,
            )
        }
    }
}

private val CHART_HEIGHT = 72.dp
private val GAP = 6.dp
private val CORNER = 4.dp
