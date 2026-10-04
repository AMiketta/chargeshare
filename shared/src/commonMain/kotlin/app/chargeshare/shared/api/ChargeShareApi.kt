package app.chargeshare.shared.api

import app.chargeshare.shared.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/**
 * Typisierter Client fuer die `/api/v1`-API (siehe legacy/go-backend/internal/api/router.go).
 * Alle Methoden sind `suspend` und werfen [ApiException] bei Fehlerantworten.
 */
class ChargeShareApi(
    private val http: HttpClient,
    private val tokenStore: TokenStore,
) {
    private inline fun <reified T> HttpRequestBuilder.json(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    /**
     * Speichert die Tokens und verwirft die im Bearer-Plugin zwischengespeicherten.
     * Sonst sendet der Client nach einem Kontowechsel weiter das Token des vorherigen Nutzers.
     */
    private suspend fun saveTokens(accessToken: String, refreshToken: String) {
        tokenStore.save(accessToken, refreshToken)
        clearCachedBearerTokens()
    }

    private fun clearCachedBearerTokens() {
        http.authProviders.filterIsInstance<BearerAuthProvider>().forEach { it.clearToken() }
    }

    // ---- Auth -------------------------------------------------------------------------------

    suspend fun register(email: String, password: String, name: String): AuthResponse =
        http.post("$V1/auth/register") { json(RegisterRequest(email, password, name)) }
            .body<AuthResponse>().also { saveTokens(it.accessToken, it.refreshToken) }

    suspend fun login(email: String, password: String): AuthResponse =
        http.post("$V1/auth/login") { json(LoginRequest(email, password)) }
            .body<AuthResponse>().also { saveTokens(it.accessToken, it.refreshToken) }

    suspend fun loginWithApple(identityToken: String, fullName: String? = null): AuthResponse =
        http.post("$V1/auth/apple") { json(AppleAuthRequest(identityToken, fullName)) }
            .body<AuthResponse>().also { saveTokens(it.accessToken, it.refreshToken) }

    suspend fun refresh(refreshToken: String): TokenPair =
        http.post("$V1/auth/refresh") { json(RefreshRequest(refreshToken)) }.body<TokenPair>()

    /** Fordert eine E-Mail zum Zurücksetzen an; antwortet immer ohne Fehler, auch bei unbekannter Adresse. */
    suspend fun forgotPassword(email: String) {
        http.post("$V1/auth/forgot-password") { json(ForgotPasswordRequest(email)) }
    }

    /** Setzt das Passwort mit dem Token aus der E-Mail neu; alle Geräte müssen sich danach neu anmelden. */
    suspend fun resetPassword(token: String, newPassword: String) {
        http.post("$V1/auth/reset-password") { json(ResetPasswordRequest(token, newPassword)) }
    }

    suspend fun logout() {
        val refresh = tokenStore.refreshToken()
        if (refresh != null) {
            runCatching { http.post("$V1/auth/logout") { json(LogoutRequest(refresh)) } }
        }
        tokenStore.clear()
        clearCachedBearerTokens()
    }

    // ---- Users ------------------------------------------------------------------------------

    suspend fun me(): User = http.get("$V1/users/me").body()

    suspend fun updateMe(request: UpdateUserRequest): User =
        http.put("$V1/users/me") { json(request) }.body()

    suspend fun deleteMe() {
        http.delete("$V1/users/me")
        tokenStore.clear()
    }

    suspend fun myStats(): UserStats = http.get("$V1/users/me/stats").body()

    suspend fun updateRole(role: UserRole): User =
        http.put("$V1/users/me/role") { json(UpdateRoleRequest(role)) }.body()

    // ---- Favoriten --------------------------------------------------------------------------

    /** IDs der favorisierten Wallboxen des Nutzers, neueste zuerst. */
    suspend fun favoriteIds(): List<String> = http.get("$V1/users/me/favorites").body<FavoritesResponse>().wallboxIds

    /** Markiert die Wallbox als Favorit; mehrfaches Setzen ist unschädlich. */
    suspend fun addFavorite(wallboxId: String) {
        http.put("$V1/users/me/favorites/$wallboxId")
    }

    /** Entfernt den Favoriten; ist die Wallbox kein Favorit, passiert nichts. */
    suspend fun removeFavorite(wallboxId: String) {
        http.delete("$V1/users/me/favorites/$wallboxId")
    }

    // ---- Wallboxes --------------------------------------------------------------------------

    suspend fun searchWallboxes(search: WallboxSearch = WallboxSearch()): WallboxListResponse =
        http.get("$V1/wallboxes") {
            parameter("lat", search.lat)
            parameter("lng", search.lng)
            parameter("radius", search.radiusM)
            parameter("connector", search.connector?.let { ChargeShareJson.encodeToString(ConnectorType.serializer(), it).trim('"') })
            parameter("min_power", search.minPowerKw)
            parameter("max_price", search.maxPricePerKwh)
            parameter("limit", search.limit)
            parameter("offset", search.offset)
        }.body()

    suspend fun wallbox(id: String): Wallbox = http.get("$V1/wallboxes/$id").body()

    /** Öffentliches Profil des Hosts der Wallbox [id] (gekürzter Name, Mitglied seit, Sessions, Superhost). */
    suspend fun wallboxHost(id: String): HostProfile = http.get("$V1/wallboxes/$id/host").body()

    suspend fun createWallbox(request: CreateWallboxRequest): Wallbox =
        http.post("$V1/wallboxes") { json(request) }.body()

    suspend fun updateWallbox(id: String, request: UpdateWallboxRequest): Wallbox =
        http.put("$V1/wallboxes/$id") { json(request) }.body()

    /**
     * Lädt ein Foto hoch (`multipart/form-data`, Feld `file`) und liefert die aktualisierte Wallbox.
     * Der Server speichert daraus ein JPEG mit höchstens 1600 px Kantenlänge; erlaubt sind JPEG und PNG.
     */
    suspend fun uploadWallboxPhoto(id: String, bytes: ByteArray, fileName: String = "photo.jpg", mimeType: String = "image/jpeg"): Wallbox =
        http.submitFormWithBinaryData(
            url = "$V1/wallboxes/$id/photos",
            formData = formData {
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    },
                )
            },
        ).body()

    /** Entfernt das Foto [photoPath] (ein Eintrag aus [Wallbox.photos]) und liefert die aktualisierte Wallbox. */
    suspend fun deleteWallboxPhoto(id: String, photoPath: String): Wallbox =
        http.delete("$V1/wallboxes/$id/photos/${photoPath.substringAfterLast('/')}").body()

    /** Koordinaten zur Adresse. `404` = Adresse unbekannt, `503` = Adresssuche gerade nicht verfügbar. */
    suspend fun geocode(request: GeocodeRequest): GeocodeResponse = http.post("$V1/geocode") { json(request) }.body()

    suspend fun deleteWallbox(id: String) {
        http.delete("$V1/wallboxes/$id")
    }

    suspend fun setWallboxOnline(id: String, isOnline: Boolean): WallboxStatusResponse =
        http.put("$V1/wallboxes/$id/status") { json(WallboxStatusRequest(isOnline)) }.body()

    suspend fun wallboxAvailability(id: String): WeeklyAvailability =
        http.get("$V1/wallboxes/$id/availability").body()

    suspend fun updateWallboxAvailability(id: String, availability: WeeklyAvailability): WeeklyAvailability =
        http.put("$V1/wallboxes/$id/availability") { json(availability) }.body()

    /** @param date `YYYY-MM-DD` (UTC); `null` = heute (Server-Default) */
    suspend fun wallboxSlots(id: String, date: String? = null): SlotsResponse =
        http.get("$V1/wallboxes/$id/slots") { parameter("date", date) }.body()

    suspend fun wallboxReviews(id: String, limit: Int? = null, offset: Int? = null): ReviewListResponse =
        http.get("$V1/wallboxes/$id/reviews") {
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    // ---- Bookings ---------------------------------------------------------------------------

    suspend fun bookings(status: BookingStatus? = null, limit: Int? = null, offset: Int? = null): BookingListResponse =
        http.get("$V1/bookings") {
            parameter("status", status?.let { ChargeShareJson.encodeToString(BookingStatus.serializer(), it).trim('"') })
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun createBooking(request: CreateBookingRequest): BookingResponse =
        http.post("$V1/bookings") { json(request) }.body()

    suspend fun booking(id: String): Booking = http.get("$V1/bookings/$id").body()

    /**
     * Das Backend antwortet auf `DELETE /bookings/:id` mit `204 No Content` (Go-Vertrag); zurueckgegeben wird
     * daher die anschliessend geladene, stornierte Buchung.
     */
    suspend fun cancelBooking(id: String, reason: String? = null): Booking {
        http.delete("$V1/bookings/$id") { if (reason != null) json(CancelBookingRequest(reason)) }
        return booking(id)
    }

    /**
     * „Jetzt bezahlen“ fuer eine offene (`pending`) Buchung: liefert das `client_secret` fuer das Zahlungsfenster
     * (Stripe-PaymentIntent wird weiterverwendet oder neu angelegt). Ist die Buchung inzwischen bezahlt, fehlt das Secret.
     */
    suspend fun payBooking(bookingId: String): BookingResponse =
        http.post("$V1/bookings/$bookingId/pay").body()

    suspend fun confirmPayment(bookingId: String): Booking =
        http.post("$V1/bookings/$bookingId/confirm-payment").body()

    // ---- Reviews ----------------------------------------------------------------------------

    suspend fun createReview(request: CreateReviewRequest): Review =
        http.post("$V1/reviews") { json(request) }.body()

    // ---- Host -------------------------------------------------------------------------------

    suspend fun hostBookings(limit: Int? = null, offset: Int? = null): BookingListResponse =
        http.get("$V1/host/bookings") {
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    /**
     * @param from `YYYY-MM-DD` @param to `YYYY-MM-DD` (beide optional, nach Beginn der Buchung)
     * @param timeZone IANA-Zeitzone (z. B. `Europe/Berlin`) für Monats- und Wochengrenzen; ohne Angabe UTC
     */
    suspend fun hostEarnings(from: String? = null, to: String? = null, timeZone: String? = null): HostEarnings =
        http.get("$V1/host/earnings") {
            parameter("from", from)
            parameter("to", to)
            parameter("tz", timeZone)
        }.body()

    suspend fun hostSessions(limit: Int? = null, offset: Int? = null): SessionListResponse =
        http.get("$V1/host/sessions") {
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    // ---- Payments (Stripe Connect) ----------------------------------------------------------

    suspend fun startPayoutOnboarding(): OnboardingLink = http.post("$V1/payments/onboard").body()

    /** Kontostatus aus der Datenbank (ohne Stripe-Abfrage). */
    suspend fun payoutStatus(): PayoutStatus = http.get("$V1/payments/payout-status").body()

    /** Fragt Stripe sofort nach dem Kontostatus, speichert ihn und liefert ihn zurück (nach der Rückkehr vom Onboarding). */
    suspend fun refreshPayoutStatus(): PayoutStatus = http.post("$V1/payments/payout-status/refresh").body()

    /** Gespeicherte Karten des Nutzers (Marke, letzte vier Ziffern, Ablauf). */
    suspend fun savedCards(): SavedCardsResponse = http.get("$V1/payments/methods").body()

    /** Entfernt eine gespeicherte Karte. */
    suspend fun removeSavedCard(paymentMethodId: String) {
        http.delete("$V1/payments/methods/$paymentMethodId")
    }

    /** Neuer SetupIntent, um eine Karte im PaymentSheet zu speichern. */
    suspend fun createSetupIntent(): SetupIntentResponse = http.post("$V1/payments/setup-intent").body()

    /** Customer Session für das PaymentSheet (gespeicherte Karten bei der Buchung). */
    suspend fun createCustomerSession(): CustomerSessionResponse = http.post("$V1/payments/customer-session").body()

    /** Quittung (PDF) einer abgeschlossenen, bezahlten Buchung. */
    suspend fun bookingReceipt(bookingId: String): ByteArray = http.get("$V1/bookings/$bookingId/receipt").body()

    /** Öffentliche Zahlungskonfiguration (Publishable Key, Händlerdaten) für Google Pay / Apple Pay / PaymentSheet. */
    suspend fun paymentConfig(): PaymentConfig = http.get("$V1/payments/config").body()

    private companion object {
        const val V1 = "api/v1"
    }
}
