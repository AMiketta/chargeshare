package app.chargeshare.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.chargeshare.presentation.onboarding.OnboardingPage
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AmberButton
import kotlinx.coroutines.launch

/**
 * Einführung beim ersten Start: vier Seiten zum Wischen, jederzeit überspringbar.
 *
 * @param onFinished wird nach der letzten Seite und bei „Überspringen“ aufgerufen
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val pages = OnboardingPage.entries
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    // Zurück geht erst eine Seite zurück; auf der ersten Seite beendet es die Einführung.
    BackHandler {
        if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } else onFinished()
    }

    Surface(modifier = modifier.fillMaxSize(), color = ChargeShareColors.Background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().height(SKIP_ROW_HEIGHT).padding(horizontal = Spacing.Small), horizontalArrangement = Arrangement.End) {
                if (!isLast) TextButton(onClick = onFinished) { Text(Strings.OnboardingSkip) }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
                OnboardingPageContent(pages[index])
            }
            PageDots(count = pages.size, current = pagerState.currentPage)
            AmberButton(
                text = if (isLast) Strings.OnboardingStart else Strings.OnboardingNext,
                onClick = {
                    if (isLast) onFinished() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.padding(Spacing.Medium),
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIllustration(page)
        Text(
            Strings.onboardingTitle(page),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.XLarge),
        )
        Text(
            Strings.onboardingBody(page),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.Medium),
        )
    }
}

/**
 * Platzhalter-Grafik im Design-Stil (grüner Kreis mit Symbol und Amber-Akzent). Die fertigen Stimmungsbilder ersetzen
 * diese Funktion; Aufrufer und Seitenlogik bleiben unverändert.
 */
@Composable
private fun OnboardingIllustration(page: OnboardingPage) {
    val (icon, accent) = illustrationOf(page)
    Box(
        modifier = Modifier.size(ILLUSTRATION_SIZE).clip(CircleShape).background(ChargeShareColors.PrimarySurface)
            .semantics { contentDescription = Strings.onboardingTitle(page) },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(INNER_RING_SIZE).clip(CircleShape).background(ChargeShareColors.PrimaryLight), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(ICON_DISC_SIZE).clip(CircleShape).background(ChargeShareColors.Primary), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = ChargeShareColors.OnPrimary, modifier = Modifier.size(ICON_SIZE))
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(ACCENT_OFFSET).size(ACCENT_SIZE).clip(CircleShape).background(ChargeShareColors.Accent), contentAlignment = Alignment.Center) {
            Icon(accent, contentDescription = null, tint = ChargeShareColors.OnAccent, modifier = Modifier.size(ACCENT_ICON_SIZE))
        }
    }
}

private fun illustrationOf(page: OnboardingPage): Pair<ImageVector, ImageVector> = when (page) {
    OnboardingPage.Find -> AppIcons.Map to AppIcons.Search
    OnboardingPage.Book -> AppIcons.Calendar to AppIcons.Star
    OnboardingPage.Charge -> AppIcons.Plug to AppIcons.Bolt
    OnboardingPage.Host -> AppIcons.Bolt to AppIcons.Person
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Small)
            .semantics { contentDescription = Strings.onboardingProgress(current + 1, count) },
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val width = animateDpAsState(if (index == current) DOT_ACTIVE_WIDTH else DOT_SIZE).value
            Box(
                modifier = Modifier.width(width).height(DOT_SIZE).clip(RoundedCornerShape(DOT_SIZE))
                    .background(if (index == current) ChargeShareColors.Primary else ChargeShareColors.Outline),
            )
        }
    }
}

private val SKIP_ROW_HEIGHT = 56.dp
private val ILLUSTRATION_SIZE = 240.dp
private val INNER_RING_SIZE = 176.dp
private val ICON_DISC_SIZE = 112.dp
private val ICON_SIZE = 56.dp
private val ACCENT_SIZE = 52.dp
private val ACCENT_ICON_SIZE = 28.dp
private val ACCENT_OFFSET = 20.dp
private val DOT_SIZE = 8.dp
private val DOT_ACTIVE_WIDTH = 24.dp
