package app.chargeshare.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.chargeshare.ui.Spacing

/** Waagerecht scrollbare Chip-Reihe, in der genau eine Option (oder keine) gewählt ist. */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.Medium),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        items(options) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option)) },
            )
        }
    }
}
