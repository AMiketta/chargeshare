package app.chargeshare.ui

import app.chargeshare.data.AppError
import app.chargeshare.payment.PaymentMethodChoice
import app.chargeshare.presentation.auth.FieldError
import app.chargeshare.presentation.host.WallboxField
import app.chargeshare.shared.model.BookingStatus

/**
 * Alle deutschen UI-Texte an einer Stelle (siehe AGENTS.md, Abschnitt 9).
 * Zwischenlösung vor Compose-Multiplatform-Resources; Texte mit Platzhaltern sind Funktionen.
 */
object Strings {
    const val AppName = "ChargeShare"

    // ---- Navigation
    const val TabSearch = "Karte"
    const val TabBookings = "Buchungen"
    const val TabHost = "Hosten"
    const val TabProfile = "Profil"
    const val Back = "Zurück"

    // ---- Allgemein
    const val Retry = "Erneut versuchen"
    const val Cancel = "Abbrechen"
    const val Loading = "Wird geladen …"
    const val Save = "Speichern"

    // ---- Anmeldung
    const val Tagline = "Wallbox-Sharing in deiner Nähe"
    const val Login = "Anmelden"
    const val Register = "Registrieren"
    const val CreateAccount = "Konto erstellen"
    const val NoAccountYet = "Noch kein Konto? Registrieren"
    const val HaveAccount = "Schon ein Konto? Anmelden"
    const val FieldName = "Name"
    const val FieldEmail = "E-Mail"
    const val FieldPassword = "Passwort"
    const val PasswordHint = "Mindestens 8 Zeichen"
    const val SessionRestoring = "Anmeldung wird geprüft …"
    const val ShowPassword = "Passwort anzeigen"
    const val HidePassword = "Passwort verbergen"

    fun fieldError(error: FieldError): String = when (error) {
        FieldError.Required -> "Bitte ausfüllen."
        FieldError.InvalidEmail -> "Bitte eine gültige E-Mail-Adresse eingeben."
        FieldError.PasswordTooShort -> "Das Passwort braucht mindestens 8 Zeichen."
    }

    // ---- Suche
    const val SearchPlaceholder = "Wallbox in der Nähe suchen …"
    const val MoreFilters = "Mehr Filter"
    const val FewerFilters = "Weniger Filter"
    const val FilterOnlyOnline = "Jetzt verfügbar"
    const val FilterAllConnectors = "Alle Stecker"
    const val FilterAnyPower = "Beliebige Leistung"
    const val FilterAnyPrice = "Beliebiger Preis"
    const val MapUnavailableTitle = "Karte hier nicht verfügbar"
    const val MapUnavailableBody = "Auf dieser Plattform gibt es noch keine Karte. Alle Wallboxen findest du in der Liste."
    const val ViewMap = "Karte"
    const val ViewList = "Liste"
    const val ShowFilters = "Filter"
    const val HideFilters = "Filter ausblenden"
    const val MapAttribution = "© OpenStreetMap-Mitwirkende"

    fun mapPinDescription(name: String, isOnline: Boolean) = "$name, ${if (isOnline) Online else Offline}"
    const val NoWallboxes = "Keine Wallbox gefunden. Vergrößere den Umkreis oder lockere die Filter."
    const val SearchCenterNote = "Suche rund um Bremen-Zentrum (Standortfreigabe folgt)."
    const val Online = "Verfügbar"
    const val Offline = "Offline"
    const val Metered = "MID-geeicht"
    const val NotMetered = "Ohne Eichzähler"
    const val DetailTitle = "Wallbox"

    fun stationsNearby(count: Int) = if (count == 1) "1 Wallbox in der Nähe" else "$count Wallboxen in der Nähe"

    fun powerFilter(kw: Double) = "${formatPowerKw(kw)}+"

    fun priceFilter(price: Double) = "bis ${formatPricePerKwh(price)}"

    fun radiusLabel(meters: Double) = "Umkreis ${formatRadius(meters)}"

    // ---- Detail
    const val Specs = "Technik"
    const val Price = "Preis"
    const val Connector = "Stecker"
    const val Power = "Leistung"
    const val Availability = "Verfügbarkeit"
    const val AvailabilityUtcNote = "Zeiten in UTC (Mitteleuropa: Winter +1 Std., Sommer +2 Std.)"
    const val Reviews = "Bewertungen"
    const val NoReviews = "Noch keine Bewertungen."
    const val BookNow = "Zeitslot buchen"
    const val OfflineCannotBook = "Diese Wallbox ist gerade offline und kann nicht gebucht werden."
    const val NoAvailability = "Keine festen Zeiten hinterlegt."
    const val ByAnonymous = "Anonym"

    fun ratingSummary(avg: Double, count: Int) =
        if (count == 0) "Noch keine Bewertung" else "${formatDecimal(avg, 1)} ($count ${if (count == 1) "Bewertung" else "Bewertungen"})"

    fun freeSlotsToday(count: Int) = when (count) {
        0 -> "Heute keine freien Slots"
        1 -> "Heute 1 freier 30-Minuten-Slot"
        else -> "Heute $count freie 30-Minuten-Slots"
    }

    fun duration(minMinutes: Int, maxMinutes: Int) = "Buchungsdauer ${durationLabel(minMinutes)} bis ${durationLabel(maxMinutes)}"

    fun ratingStars(rating: Int) = "$rating von 5 Sternen"

    // ---- Buchungsflow
    const val BookingTitle = "Zeitslot buchen"
    const val Date = "Datum"
    const val StartTime = "Startzeit"
    const val DurationLabel = "Dauer"
    const val NoFreeSlots = "An diesem Tag sind keine Slots frei."
    const val CostEstimate = "Kosten (Schätzung)"
    const val CostDisclaimer = "Maximalbetrag bei durchgehendem Laden mit voller Leistung. Abgerechnet wird nach tatsächlich geladenen kWh."
    const val Total = "Gesamt (max.)"
    const val SelectSlotFirst = "Bitte wähle eine Startzeit."
    const val BookingSuccessTitle = "Buchung bestätigt"
    const val BookingPendingTitle = "Buchung angelegt"
    const val BookingPendingBody = "Die Zahlung ist noch nicht abgeschlossen. Du findest die Buchung unter „Buchungen“."
    const val ToMyBookings = "Zu meinen Buchungen"
    const val Close = "Schließen"
    const val Booking = "Buchung"

    // ---- Zahlung
    const val PaymentTitle = "Zahlungsart"
    const val PaymentLoading = "Zahlungsarten werden geladen …"
    const val PaymentTestMode = "Testzahlung (Entwicklungsmodus): Die Buchung gilt sofort als bezahlt, es wird nichts abgebucht."
    const val PaymentPlatformUnsupported =
        "Auf diesem Gerät ist keine Zahlung möglich. Google Pay und Apple Pay gibt es nur in der ChargeShare-App für Android und iOS."
    const val PaymentServerNotConfigured = "Die Zahlung ist serverseitig noch nicht eingerichtet. Bitte versuche es später erneut."
    const val PaymentInProgressSheet = "Zahlung läuft …"
    const val PaymentInProgressConfirming = "Zahlung wird bestätigt …"
    const val PaymentInProgressDiscarding = "Buchung wird verworfen …"
    const val PaymentCanceled = "Zahlung abgebrochen. Die Buchung ist angelegt, aber noch nicht bezahlt – dein Slot bleibt kurz reserviert."
    const val PaymentFailed = "Die Zahlung konnte nicht abgeschlossen werden. Bitte versuche es erneut oder wähle eine andere Zahlungsart."
    const val PaymentNotConfirmed = "Die Zahlung wurde ausgeführt, aber noch nicht bestätigt. Bitte versuche es gleich noch einmal."
    const val PayAgain = "Erneut bezahlen"
    const val CheckPayment = "Zahlung prüfen"
    const val DiscardBooking = "Buchung verwerfen"

    fun paymentMethod(method: PaymentMethodChoice) = when (method) {
        PaymentMethodChoice.GOOGLE_PAY -> "Google Pay"
        PaymentMethodChoice.APPLE_PAY -> "Apple Pay"
        PaymentMethodChoice.CARD -> "Karte und weitere"
    }

    fun payAndBookWithPrice(total: Double?) = if (total == null) "Buchen und bezahlen" else "Buchen und bezahlen · ${formatEuro(total)}"

    fun bookNowWithPrice(total: Double?) = if (total == null) "Jetzt buchen" else "Jetzt buchen · ${formatEuro(total)}"

    fun estimateLine(kwh: Double, price: Double) = "ca. ${formatDecimal(kwh, 0)} kWh × ${formatDecimal(price, 2)} €"

    fun durationLabel(minutes: Int): String {
        val hours = minutes / MINUTES_PER_HOUR
        val rest = minutes % MINUTES_PER_HOUR
        return when {
            hours == 0 -> "$rest Min."
            rest == 0 -> "$hours Std."
            else -> "$hours Std. $rest Min."
        }
    }

    private const val MINUTES_PER_HOUR = 60

    // ---- Buchungsliste
    const val BookingsTitle = "Meine Buchungen"
    const val BookingsUpcoming = "Kommend"
    const val BookingsPast = "Vergangen"
    const val NoUpcomingBookings = "Keine kommenden Buchungen. Finde eine Wallbox und buche deinen ersten Slot."
    const val NoPastBookings = "Noch keine vergangenen Buchungen."
    const val CancelBooking = "Stornieren"
    const val CancelBookingTitle = "Buchung stornieren?"
    const val CancelBookingConfirm = "Ja, stornieren"
    const val CancelBookingKeep = "Behalten"
    const val UnknownWallbox = "Wallbox"
    const val ReviewAction = "Bewerten"
    const val Reviewed = "Bewertet"
    const val ReviewTitle = "Wallbox bewerten"
    const val ReviewCommentLabel = "Kommentar (optional)"
    const val ReviewSubmit = "Senden"
    const val ReviewCommentTooLong = "Der Kommentar ist zu lang."

    fun reviewBody(name: String) = "Wie war dein Ladevorgang bei „$name“?"

    fun reviewStarOption(stars: Int) = if (stars == 1) "1 Stern" else "$stars Sterne"

    fun reviewCommentCounter(length: Int, max: Int) = "$length / $max"
    const val PayNow = "Jetzt bezahlen"
    const val PendingPaymentHint = "Noch nicht bezahlt. Der Slot ist nur kurz reserviert und wird sonst automatisch freigegeben."
    const val PaymentExpiredHint = "Nicht rechtzeitig bezahlt – die Buchung wurde automatisch storniert."

    fun cancelBookingBody(name: String) = "Möchtest du die Buchung bei „$name“ wirklich stornieren? Eine bereits bezahlte Buchung wird erstattet."

    fun bookingStatus(status: BookingStatus) = when (status) {
        BookingStatus.PENDING -> "Zahlung ausstehend"
        BookingStatus.CONFIRMED -> "Bestätigt"
        BookingStatus.ACTIVE -> "Lädt gerade"
        BookingStatus.COMPLETED -> "Abgeschlossen"
        BookingStatus.CANCELLED -> "Storniert"
        BookingStatus.FAILED -> "Fehlgeschlagen"
    }

    // ---- Host
    const val HostTitle = "Host-Bereich"
    const val BecomeHostTitle = "Wallbox teilen und verdienen"
    const val BecomeHostBody =
        "Gib deine Wallbox zu selbst gewählten Zeiten frei, lege den Preis fest und erhalte automatisch deine Einnahmen."
    const val BecomeHost = "Host werden"
    const val Earnings = "Einnahmen"
    const val EarningsTotal = "Gesamt"
    const val EarningsMonth = "Dieser Monat"
    const val Sessions = "Sessions"
    const val AverageRating = "Bewertung"
    const val MyWallboxes = "Meine Wallboxen"
    const val NoMyWallboxes = "Du hast noch keine Wallbox angelegt."
    const val AddWallbox = "Wallbox anlegen"
    const val RecentSessions = "Letzte Sessions"
    const val NoSessions = "Noch keine Ladevorgänge."
    const val Released = "Freigegeben"
    const val Blocked = "Gesperrt"
    const val ReleaseSwitchDescription = "Wallbox für Buchungen freigeben"
    const val PayoutNote = "Die Auszahlung per Stripe Connect ist in der App noch nicht angebunden."

    fun sessionTitle(driverName: String?) = driverName ?: "Fahrer"

    fun sessionKwh(kwh: Double?) = if (kwh == null) "–" else formatKwh(kwh)

    // ---- Wallbox anlegen
    const val CreateWallboxTitle = "Wallbox anlegen"
    const val FieldWallboxName = "Name der Wallbox"
    const val FieldAddress = "Straße und Hausnummer"
    const val FieldCity = "Ort"
    const val FieldPostalCode = "PLZ"
    const val FieldLat = "Breitengrad"
    const val FieldLng = "Längengrad"
    const val FieldPowerKw = "Leistung (kW)"
    const val FieldPrice = "Preis (€/kWh)"
    const val FieldMetered = "MID-geeichter Zähler vorhanden"
    const val MeteredHint = "Für entgeltliche Stromabgabe ist ein geeichter Zähler vorgeschrieben (MessEG)."
    const val FieldAvailabilityStart = "Von (UTC)"
    const val FieldAvailabilityEnd = "Bis (UTC)"
    const val AvailabilityUtcHint = "Zeiten gelten in UTC (Mitteleuropa: im Winter −1 Std., im Sommer −2 Std.)."
    const val FieldDays = "Tage"
    const val CoordinatesHint = "Koordinaten bitte manuell eintragen (z. B. aus einer Karten-App); eine Adresssuche folgt."
    const val WallboxCreated = "Wallbox angelegt"

    fun wallboxFieldError(field: WallboxField) = when (field) {
        WallboxField.Name -> "Bitte einen Namen (max. 200 Zeichen) eingeben."
        WallboxField.Address -> "Bitte die Adresse eingeben."
        WallboxField.City -> "Bitte den Ort eingeben."
        WallboxField.PostalCode -> "Bitte die PLZ eingeben."
        WallboxField.Lat -> "Breitengrad zwischen −90 und 90 eingeben."
        WallboxField.Lng -> "Längengrad zwischen −180 und 180 eingeben."
        WallboxField.PowerKw -> "Leistung muss größer als 0 sein."
        WallboxField.Price -> "Preis muss größer als 0 sein."
        WallboxField.AvailabilityStart -> "Uhrzeit als HH:MM eingeben."
        WallboxField.AvailabilityEnd -> "Uhrzeit als HH:MM eingeben, nach dem Beginn."
        WallboxField.Days -> "Mindestens einen Tag wählen."
    }

    // ---- Profil
    const val ProfileTitle = "Profil"
    const val Verified = "Verifiziert"
    const val NotVerified = "E-Mail nicht verifiziert"
    const val RoleDriver = "Fahrer"
    const val RoleHost = "Host"
    const val RoleBoth = "Fahrer und Host"
    const val RoleAdmin = "Administrator"
    const val Logout = "Abmelden"
    const val EditProfile = "Profil bearbeiten"
    const val EditProfileTitle = "Profil bearbeiten"
    const val FieldProfileName = "Name"
    const val FieldProfilePhone = "Telefon (optional)"
    const val ProfileNameError = "Bitte gib einen Namen mit höchstens 100 Zeichen ein."
    const val ProfilePhoneError = "Bitte gib eine gültige Telefonnummer ein, z. B. +49 421 1234567."
    const val ProfilePhoneHint = "Nur sichtbar für dich. Leer lassen, um die Nummer zu entfernen."
    const val DeleteAccount = "Konto löschen"
    const val DeleteAccountTitle = "Konto wirklich löschen?"
    const val DeleteAccountBody =
        "Dein Konto sowie deine Buchungen und Bewertungen werden unwiderruflich gelöscht (DSGVO)."
    const val DeleteAccountConfirm = "Endgültig löschen"
    const val PrivacyNote = "Datenschutz: Wir erheben nur Daten, die für Buchung und Abrechnung nötig sind. Der Standort wird nur mit deiner Einwilligung genutzt."

    // ---- Fehler
    fun errorMessage(error: AppError): String = when (error) {
        AppError.Network -> "Keine Verbindung zum Server. Bitte prüfe deine Internetverbindung und versuche es erneut."
        AppError.Unauthorized -> "Bitte melde dich erneut an."
        AppError.Forbidden -> "Dafür fehlt dir die Berechtigung."
        AppError.NotFound -> "Der Eintrag wurde nicht gefunden."
        AppError.Conflict -> "Dieser Zeitslot wurde gerade vergeben. Bitte wähle einen anderen."
        is AppError.Validation -> "Die Eingabe wurde abgelehnt. Bitte prüfe deine Angaben."
        AppError.PaymentRequired -> "Die Zahlung konnte nicht abgeschlossen werden."
        AppError.Server -> "Der Server hat ein Problem. Bitte versuche es später erneut."
        AppError.Unknown -> "Etwas ist schiefgelaufen. Bitte versuche es erneut."
    }

    /** Beim Login bedeutet 401 „Zugangsdaten falsch“, nicht „Sitzung abgelaufen“. */
    fun loginErrorMessage(error: AppError): String =
        if (error == AppError.Unauthorized) "E-Mail oder Passwort ist falsch." else errorMessage(error)

    /** Bei der Registrierung bedeutet 409 „E-Mail schon vergeben“. */
    fun registerErrorMessage(error: AppError): String =
        if (error == AppError.Conflict) "Diese E-Mail-Adresse ist bereits registriert." else errorMessage(error)
}
