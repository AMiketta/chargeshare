package app.chargeshare.ui.host

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.chargeshare.presentation.host.PhotoIssue
import app.chargeshare.shared.model.WallboxLimits
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.components.AppIconButton
import app.chargeshare.ui.components.ErrorText
import app.chargeshare.ui.components.PhotoImage
import app.chargeshare.ui.components.PrimaryButton
import app.chargeshare.ui.components.SectionTitle

/**
 * Fotos einer Wallbox im Bearbeiten-Formular: Vorschaubilder mit „Entfernen“ und „Foto hinzufügen“.
 * Beim Anlegen ([isEdit] = `false`) gibt es nur einen Hinweis, weil Fotos eine bestehende Wallbox brauchen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotosSection(
    wallboxName: String,
    photos: List<String>,
    isEdit: Boolean,
    canPick: Boolean,
    isBusy: Boolean,
    issue: PhotoIssue?,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SectionTitle(Strings.PhotosTitle)
        if (!isEdit) {
            Text(Strings.PhotosAfterCreateHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Text(Strings.PhotosHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            photos.forEachIndexed { index, path ->
                Box(modifier = Modifier.size(THUMB_SIZE).clip(RoundedCornerShape(THUMB_RADIUS)).background(ChargeShareColors.Surface2)) {
                    PhotoImage(
                        path = path,
                        contentDescription = Strings.photoDescription(wallboxName, index, photos.size),
                        modifier = Modifier.size(THUMB_SIZE).aspectRatio(1f),
                    )
                    AppIconButton(
                        icon = AppIcons.Delete,
                        contentDescription = Strings.RemovePhoto,
                        onClick = { onRemove(path) },
                        modifier = Modifier.align(Alignment.TopEnd),
                        tint = ChargeShareColors.Error,
                        enabled = !isBusy,
                    )
                }
            }
        }
        issue?.let { ErrorText(Strings.photoIssueMessage(it)) }
        when {
            !canPick -> Text(Strings.PhotosUnsupported, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            photos.size >= WallboxLimits.MAX_PHOTOS -> Text(Strings.PhotoLimitReached, style = MaterialTheme.typography.bodySmall)
            else -> PrimaryButton(
                text = if (isBusy) Strings.UploadingPhoto else Strings.AddPhoto,
                onClick = onAdd,
                enabled = !isBusy,
            )
        }
    }
}

private val THUMB_SIZE = 96.dp
private val THUMB_RADIUS = 12.dp
