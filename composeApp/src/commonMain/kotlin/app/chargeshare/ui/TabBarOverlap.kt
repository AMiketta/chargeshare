package app.chargeshare.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Höhe, um die eine über dem Inhalt liegende Tab-Leiste den unteren Bildschirmrand überdeckt (inkl. Home-Indicator).
 *
 * Tab-Screens zeichnen Hintergründe (Karte, Listen) bis ganz unten, polstern aber **scrollbare Inhalte und Bedienelemente**
 * um diesen Wert, damit nichts dauerhaft unter der Leiste verschwindet – z. B. `contentPadding` einer `LazyColumn`
 * oder ein `Spacer` am Ende einer `verticalScroll`-Spalte.
 */
val LocalTabBarOverlap = staticCompositionLocalOf<Dp> { 0.dp }
