package app.chargeshare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.chargeshare.data.AppError
import app.chargeshare.resources.leer
import app.chargeshare.resources.Res
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Elevation
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import org.jetbrains.compose.resources.painterResource

/** Tiefgrüner Kopfbereich (Hero) mit weißer Schrift, optional mit Zurück-Pfeil. */
@Composable
fun HeroHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ChargeShareColors.Primary)
            .statusBarsPadding()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                AppIconButton(AppIcons.Back, Strings.Back, onBack)
            }
            Column(modifier = Modifier.padding(vertical = Spacing.Small)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = ChargeShareColors.OnPrimary)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.OnPrimary.copy(alpha = HERO_SUBTITLE_ALPHA))
                }
            }
        }
        content()
    }
}

private const val HERO_SUBTITLE_ALPHA = 0.85f

/**
 * Karte im Design-Stil: Radius 20 dp, dezenter Schatten und Rand. Mit [onClick] ist die ganze Karte klickbar.
 * Der Inhalt bringt sein eigenes Padding mit (siehe [SurfaceCard]).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = ChargeShareColors.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card)
    val border = BorderStroke(CARD_BORDER_WIDTH, ChargeShareColors.Outline)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
    }
}

private val CARD_BORDER_WIDTH = 1.dp

/** Weiße Karte mit Schatten, Innenabstand und Abstand zwischen den Zeilen. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Small), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

/** Kleines Label („Chip“) für Eigenschaften wie Stecker oder Leistung. */
@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = ChargeShareColors.PrimarySurface,
    contentColor: Color = ChargeShareColors.PrimaryText,
) {
    Surface(modifier = modifier, shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Spacing.Small + Spacing.XSmall, vertical = Spacing.XSmall))
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Fehleranzeige mit verständlichem deutschem Text und „Erneut versuchen“. */
@Composable
fun ErrorBox(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = Strings.errorMessage(error),
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        PrimaryButton(Strings.Retry, onRetry, modifier = Modifier.padding(horizontal = Spacing.XLarge))
    }
}

/** Inline-Fehlerzeile unter Formularen. */
@Composable
fun ErrorText(message: String, modifier: Modifier = Modifier) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = modifier)
}

/** Inline-Hinweis für Erfolgsmeldungen unter Formularen. */
@Composable
fun SuccessText(message: String, modifier: Modifier = Modifier) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = ChargeShareColors.PrimaryText, modifier = modifier)
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(Spacing.Large), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * Leerzustand der Suche: Stimmungsbild „keine Stationen“ plus Hinweistext.
 * Wird in Liste und Kartenansicht genutzt, wenn der Umkreis keine Wallboxen liefert.
 */
@Composable
fun SearchEmptyBox(modifier: Modifier = Modifier, message: String = Strings.NoWallboxes) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Image(
            painter = painterResource(Res.drawable.leer),
            contentDescription = Strings.NoWallboxes,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SEARCH_EMPTY_IMAGE_MAX_HEIGHT)
                .aspectRatio(SEARCH_EMPTY_ASPECT)
                .clip(MaterialTheme.shapes.large),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val SEARCH_EMPTY_ASPECT = 16f / 9f

/** Obergrenze fürs Leerzustands-Bild, damit darunter noch Karte bzw. Liste sichtbar bleibt. */
private val SEARCH_EMPTY_IMAGE_MAX_HEIGHT = 160.dp
