package app.chargeshare.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.chargeshare.data.ImageLoader
import app.chargeshare.shared.model.PhotoPaths

/** Der [ImageLoader] der App; `App()` setzt ihn. Ohne ihn (Vorschau, Tests) bleiben nur die Platzhalter sichtbar. */
val LocalImageLoader = compositionLocalOf<ImageLoader?> { null }

/**
 * Zeigt das Wallbox-Foto [path] (Eintrag aus `Wallbox.photos`) oder, solange es lädt bzw. fehlt, den [placeholder].
 *
 * @param thumbnail `true` = kleine Fassung (Liste, Karte), `false` = große Fassung (Detailseite)
 */
@Composable
fun PhotoImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    thumbnail: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {},
) {
    val loader = LocalImageLoader.current
    val resolved = path?.let { if (thumbnail) PhotoPaths.thumbnail(it) else it }
    var bitmap by remember(resolved, loader) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(resolved, loader) {
        bitmap = if (loader != null && resolved != null) loader.load(resolved) else null
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded, contentDescription, modifier, contentScale = contentScale)
    } else {
        placeholder()
    }
}
