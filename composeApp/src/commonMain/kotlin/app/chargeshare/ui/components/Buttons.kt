package app.chargeshare.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing

/** Amber-Button: Haupthandlung auf Detail- und Bestätigungsseiten (z. B. „Zeitslot buchen“). */
@Composable
fun AmberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.MinTouchTarget),
        colors = ButtonDefaults.buttonColors(
            containerColor = ChargeShareColors.Accent,
            contentColor = ChargeShareColors.OnAccent,
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ChargeShareColors.OnAccent)
            }
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Grüner Hauptbutton (Anmelden, Speichern, „Jetzt buchen“), optional mit [leadingIcon]. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.MinTouchTarget),
        colors = ButtonDefaults.buttonColors(
            containerColor = ChargeShareColors.Primary,
            contentColor = ChargeShareColors.OnPrimary,
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ChargeShareColors.OnPrimary)
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Icon-Button mit Pflicht-[contentDescription] für Screenreader. */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.defaultMinSize(Spacing.MinTouchTarget, Spacing.MinTouchTarget)) {
        Icon(icon, contentDescription = contentDescription)
    }
}
