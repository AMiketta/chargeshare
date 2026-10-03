package app.chargeshare.platform

/**
 * Ob die Plattform die interaktive Karte (OpenStreetMap-Kacheln) anzeigt.
 * Android und Desktop = `true`; iOS und Web = `false` (dort zeigt die Suche nur die Liste, siehe README „Karte“).
 */
expect val isMapSupported: Boolean
