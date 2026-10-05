package app.chargeshare.platform

/**
 * Ob die Plattform die interaktive Karte (OpenStreetMap-Kacheln) anzeigt.
 * Android, Desktop und iOS = `true`; Web = `false` (dort zeigt die Suche nur die Liste, siehe README „Karte“).
 */
expect val isMapSupported: Boolean
