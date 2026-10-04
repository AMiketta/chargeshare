package app.chargeshare.testing

import app.chargeshare.data.AppError
import app.chargeshare.data.AuthRepository
import app.chargeshare.data.BookingCreation
import app.chargeshare.data.BookingRepository
import app.chargeshare.data.GeoPoint
import app.chargeshare.data.HostRepository
import app.chargeshare.data.PaymentRepository
import app.chargeshare.data.Outcome
import app.chargeshare.data.SessionState
import app.chargeshare.data.WallboxFilter
import app.chargeshare.data.WallboxRepository
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.ChargingSession
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.CreateWallboxRequest
import app.chargeshare.shared.model.UpdateWallboxRequest
import app.chargeshare.shared.model.HostEarnings
import app.chargeshare.shared.model.PaymentConfig
import app.chargeshare.shared.model.PaymentProviderKind
import app.chargeshare.shared.model.Review
import app.chargeshare.shared.model.User
import app.chargeshare.shared.model.UserRole
import app.chargeshare.shared.model.UserStats
import app.chargeshare.shared.model.Wallbox
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

val NOW: Instant = Instant.parse("2026-10-05T08:00:00Z")

/** Feste Uhr für deterministische Tests. */
class FixedClock(private val instant: Instant = NOW) : Clock {
    override fun now(): Instant = instant
}

fun testUser(role: UserRole = UserRole.DRIVER, id: String = "user-1") = User(
    id = id,
    email = "demo@chargeshare.local",
    name = "Demo Fahrer",
    role = role,
    createdAt = NOW,
    updatedAt = NOW,
)

fun testWallbox(
    id: String = "wb-1",
    name: String = "Gärtnerstraße 12",
    isOnline: Boolean = true,
    powerKw: Double = 11.0,
    pricePerKwh: Double = 0.34,
    ownerId: String = "host-1",
    minDurationMin: Int = 30,
    maxDurationMin: Int = 480,
) = Wallbox(
    id = id,
    ownerId = ownerId,
    name = name,
    address = "Gärtnerstraße 12",
    city = "Bremen",
    postalCode = "28209",
    lat = 53.08,
    lng = 8.83,
    powerKw = powerKw,
    connectorType = ConnectorType.TYPE2,
    pricePerKwh = pricePerKwh,
    minDurationMin = minDurationMin,
    maxDurationMin = maxDurationMin,
    isOnline = isOnline,
    createdAt = NOW,
    updatedAt = NOW,
)

fun testBooking(
    id: String = "b-1",
    status: BookingStatus = BookingStatus.CONFIRMED,
    start: Instant = Instant.parse("2026-10-06T10:00:00Z"),
    end: Instant = Instant.parse("2026-10-06T12:00:00Z"),
) = Booking(
    id = id,
    wallboxId = "wb-1",
    driverId = "user-1",
    startTime = start,
    endTime = end,
    status = status,
    pricePerKwh = 0.34,
    totalAmount = 7.48,
    createdAt = NOW,
    updatedAt = NOW,
    wallbox = testWallbox(),
)

class FakeAuthRepository(initial: SessionState = SessionState.SignedOut) : AuthRepository {
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<SessionState> = state

    var loginResult: Outcome<User> = Outcome.Success(testUser())
    var registerResult: Outcome<User> = Outcome.Success(testUser())
    var becomeHostResult: Outcome<User> = Outcome.Success(testUser(UserRole.HOST))
    var deleteResult: Outcome<Unit> = Outcome.Success(Unit)
    var updateProfileResult: Outcome<User>? = null
    var statsResult: Outcome<UserStats> = Outcome.Success(UserStats())
    var statsCalls = 0
    val profileUpdates = mutableListOf<Pair<String, String>>()
    var restoredState: SessionState = SessionState.SignedOut

    val loginCalls = mutableListOf<Pair<String, String>>()
    val registerCalls = mutableListOf<Triple<String, String, String>>()
    var logoutCalls = 0

    override suspend fun restoreSession() {
        state.value = restoredState
    }

    override suspend fun login(email: String, password: String): Outcome<User> {
        loginCalls += email to password
        return loginResult.also { if (it is Outcome.Success) state.value = SessionState.SignedIn(it.value) }
    }

    override suspend fun register(name: String, email: String, password: String): Outcome<User> {
        registerCalls += Triple(name, email, password)
        return registerResult.also { if (it is Outcome.Success) state.value = SessionState.SignedIn(it.value) }
    }

    override suspend fun logout() {
        logoutCalls++
        state.value = SessionState.SignedOut
    }

    override suspend fun becomeHost(): Outcome<User> =
        becomeHostResult.also { if (it is Outcome.Success) state.value = SessionState.SignedIn(it.value) }

    override suspend fun updateProfile(name: String, phone: String): Outcome<User> {
        profileUpdates += name to phone
        val result = updateProfileResult ?: Outcome.Success(testUser().copy(name = name, phone = phone.ifEmpty { null }))
        if (result is Outcome.Success) state.value = SessionState.SignedIn(result.value)
        return result
    }

    override suspend fun stats(): Outcome<UserStats> {
        statsCalls++
        return statsResult
    }

    override suspend fun deleteAccount(): Outcome<Unit> =
        deleteResult.also { if (it is Outcome.Success) state.value = SessionState.SignedOut }
}

class FakeWallboxRepository : WallboxRepository {
    var searchResult: Outcome<List<Wallbox>> = Outcome.Success(emptyList())
    var wallboxResult: Outcome<Wallbox> = Outcome.Success(testWallbox())
    var reviewsResult: Outcome<List<Review>> = Outcome.Success(emptyList())

    /** Freie Slots je lokalem Tag; nicht eingetragene Tage haben keine Slots. */
    val slotsByDay = mutableMapOf<LocalDate, Outcome<List<Instant>>>()

    val searches = mutableListOf<Pair<GeoPoint, WallboxFilter>>()

    override suspend fun search(center: GeoPoint, filter: WallboxFilter): Outcome<List<Wallbox>> {
        searches += center to filter
        return searchResult
    }

    override suspend fun wallbox(id: String): Outcome<Wallbox> = wallboxResult

    override suspend fun reviews(wallboxId: String): Outcome<List<Review>> = reviewsResult

    override suspend fun freeSlotStarts(wallboxId: String, day: LocalDate, zone: TimeZone): Outcome<List<Instant>> =
        slotsByDay[day] ?: Outcome.Success(emptyList())
}

class FakeBookingRepository : BookingRepository {
    var createResult: Outcome<BookingCreation> = Outcome.Success(BookingCreation(testBooking(), clientSecret = null))
    var confirmPaymentResult: Outcome<Booking> = Outcome.Success(testBooking())
    var listResult: Outcome<List<Booking>> = Outcome.Success(emptyList())
    var startPaymentResult: Outcome<BookingCreation> = Outcome.Success(BookingCreation(testBooking(status = BookingStatus.PENDING), clientSecret = "pi_1_secret_x"))
    var cancelResult: Outcome<Booking>? = null
    var reviewResult: Outcome<Review>? = null
    val reviews = mutableListOf<Triple<String, Int, String?>>()

    val created = mutableListOf<Triple<String, Instant, Instant>>()
    val cancelled = mutableListOf<String>()
    val confirmedPayments = mutableListOf<String>()
    val startedPayments = mutableListOf<String>()

    override suspend fun create(wallboxId: String, start: Instant, end: Instant): Outcome<BookingCreation> {
        created += Triple(wallboxId, start, end)
        return createResult
    }

    override suspend fun confirmPayment(bookingId: String): Outcome<Booking> {
        confirmedPayments += bookingId
        return confirmPaymentResult
    }

    override suspend fun startPayment(bookingId: String): Outcome<BookingCreation> {
        startedPayments += bookingId
        return startPaymentResult
    }

    override suspend fun review(bookingId: String, rating: Int, comment: String?): Outcome<Review> {
        reviews += Triple(bookingId, rating, comment)
        return reviewResult ?: Outcome.Success(
            Review(id = "r-1", bookingId = bookingId, wallboxId = "wb-1", authorId = "user-1", rating = rating, comment = comment, createdAt = NOW),
        )
    }

    override suspend fun list(): Outcome<List<Booking>> = listResult

    override suspend fun cancel(bookingId: String): Outcome<Booking> {
        cancelled += bookingId
        return cancelResult ?: Outcome.Success(testBooking(id = bookingId, status = BookingStatus.CANCELLED).copy(wallbox = null))
    }
}

class FakeHostRepository : HostRepository {
    var earningsResult: Outcome<HostEarnings> = Outcome.Success(HostEarnings(total = 100.0, month = 48.2, sessions = 12, avgRating = 4.9))
    var sessionsResult: Outcome<List<ChargingSession>> = Outcome.Success(emptyList())
    var wallboxesResult: Outcome<List<Wallbox>> = Outcome.Success(emptyList())
    var createResult: Outcome<Wallbox> = Outcome.Success(testWallbox())
    var setOnlineResult: Outcome<Boolean>? = null

    var updateResult: Outcome<Wallbox>? = null

    val createdRequests = mutableListOf<CreateWallboxRequest>()
    val updatedRequests = mutableListOf<Pair<String, UpdateWallboxRequest>>()

    override suspend fun earnings(): Outcome<HostEarnings> = earningsResult

    override suspend fun sessions(): Outcome<List<ChargingSession>> = sessionsResult

    override suspend fun myWallboxes(ownerId: String): Outcome<List<Wallbox>> = wallboxesResult

    override suspend fun createWallbox(request: CreateWallboxRequest): Outcome<Wallbox> {
        createdRequests += request
        return createResult
    }

    override suspend fun updateWallbox(wallboxId: String, request: UpdateWallboxRequest): Outcome<Wallbox> {
        updatedRequests += wallboxId to request
        return updateResult ?: Outcome.Success(testWallbox(wallboxId))
    }

    override suspend fun setOnline(wallboxId: String, isOnline: Boolean): Outcome<Boolean> =
        setOnlineResult ?: Outcome.Success(isOnline)
}

/** Entwicklungsmodus: der Server bucht ohne Zahlungsfenster. */
fun fakePaymentConfig() = PaymentConfig(
    provider = PaymentProviderKind.FAKE,
    merchantDisplayName = "ChargeShare",
    merchantCountryCode = "DE",
    currency = "eur",
    googlePayTestEnvironment = true,
)

/** Echte Zahlung über Stripe (Testschlüssel, Apple Pay eingerichtet). */
fun stripePaymentConfig(applePayMerchantId: String? = "merchant.app.chargeshare") = fakePaymentConfig().copy(
    provider = PaymentProviderKind.STRIPE,
    publishableKey = "pk_test_123",
    applePayMerchantId = applePayMerchantId,
)

class FakePaymentRepository(var configResult: Outcome<PaymentConfig> = Outcome.Success(fakePaymentConfig())) : PaymentRepository {
    var configCalls = 0

    override suspend fun config(): Outcome<PaymentConfig> {
        configCalls++
        return configResult
    }
}

fun failure(error: AppError = AppError.Network) = Outcome.Failure(error)
