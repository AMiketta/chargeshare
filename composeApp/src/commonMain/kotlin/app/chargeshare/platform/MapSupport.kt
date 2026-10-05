package app.chargeshare.platform

/**
 * Ob die Plattform die interaktive Karte anzeigt.
 * Android/Desktop (MapCompose+OSM) und iOS (MapKit) = `true`; Web = `false` (dort zeigt die Suche nur die Liste, siehe README „Karte“).
 */
expect val isMapSupported: Boolean
