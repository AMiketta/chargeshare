package app.chargeshare.server.db

import app.chargeshare.server.util.toInstant2
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.ChargingSession
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.Review
import app.chargeshare.shared.model.User
import app.chargeshare.shared.model.UserRole
import app.chargeshare.shared.model.Wallbox
import org.jetbrains.exposed.v1.core.ResultRow

fun String.toUserRole(): UserRole = UserRole.valueOf(uppercase())
fun UserRole.dbValue(): String = name.lowercase()
fun String.toConnector(): ConnectorType = ConnectorType.valueOf(uppercase())
fun ConnectorType.dbValue(): String = name.lowercase()
fun String.toBookingStatus(): BookingStatus = BookingStatus.valueOf(uppercase())
fun BookingStatus.dbValue(): String = name.lowercase()

fun ResultRow.toUser(): User = User(
    id = this[Users.id].toString(),
    email = this[Users.email],
    name = this[Users.name],
    phone = this[Users.phone],
    role = this[Users.role].toUserRole(),
    stripeCustomerId = this[Users.stripeCustomerId],
    stripeAccountId = this[Users.stripeAccountId],
    avatarUrl = this[Users.avatarUrl],
    isVerified = this[Users.isVerified],
    createdAt = this[Users.createdAt].toInstant2(),
    updatedAt = this[Users.updatedAt].toInstant2(),
)

fun ResultRow.toWallbox(distanceM: Double? = null): Wallbox = Wallbox(
    id = this[Wallboxes.id].toString(),
    ownerId = this[Wallboxes.ownerId].toString(),
    name = this[Wallboxes.name],
    address = this[Wallboxes.address],
    city = this[Wallboxes.city],
    postalCode = this[Wallboxes.postalCode],
    lat = this[Wallboxes.lat],
    lng = this[Wallboxes.lng],
    powerKw = this[Wallboxes.powerKw].toDouble(),
    connectorType = this[Wallboxes.connectorType].toConnector(),
    pricePerKwh = this[Wallboxes.pricePerKwh].toDouble(),
    minDurationMin = this[Wallboxes.minDurationMin],
    maxDurationMin = this[Wallboxes.maxDurationMin],
    isOnline = this[Wallboxes.isOnline],
    isMetered = this[Wallboxes.isMetered],
    ocppId = this[Wallboxes.ocppId],
    model = this[Wallboxes.model],
    manufacturer = this[Wallboxes.manufacturer],
    photos = this[Wallboxes.photos] ?: emptyList(),
    availability = this[Wallboxes.availability],
    ratingAvg = this[Wallboxes.ratingAvg]?.toDouble() ?: 0.0,
    ratingCount = this[Wallboxes.ratingCount] ?: 0,
    createdAt = this[Wallboxes.createdAt].toInstant2(),
    updatedAt = this[Wallboxes.updatedAt].toInstant2(),
    distanceM = distanceM,
)

fun ResultRow.toBooking(wallbox: Wallbox? = null, driver: User? = null, reviewed: Boolean = false): Booking = Booking(
    id = this[Bookings.id].toString(),
    wallboxId = this[Bookings.wallboxId].toString(),
    driverId = this[Bookings.driverId].toString(),
    startTime = this[Bookings.startTime].toInstant2(),
    endTime = this[Bookings.endTime].toInstant2(),
    status = this[Bookings.status].toBookingStatus(),
    estimatedKwh = this[Bookings.estimatedKwh]?.toDouble(),
    actualKwh = this[Bookings.actualKwh]?.toDouble(),
    pricePerKwh = this[Bookings.pricePerKwh].toDouble(),
    totalAmount = this[Bookings.totalAmount]?.toDouble(),
    stripePaymentIntentId = this[Bookings.stripePaymentIntentId],
    stripeChargeId = this[Bookings.stripeChargeId],
    ocppTransactionId = this[Bookings.ocppTransactionId],
    cancelReason = this[Bookings.cancelReason],
    reviewed = reviewed,
    createdAt = this[Bookings.createdAt].toInstant2(),
    updatedAt = this[Bookings.updatedAt].toInstant2(),
    wallbox = wallbox,
    driver = driver,
)

fun ResultRow.toReview(author: User? = null): Review = Review(
    id = this[Reviews.id].toString(),
    bookingId = this[Reviews.bookingId].toString(),
    wallboxId = this[Reviews.wallboxId].toString(),
    authorId = this[Reviews.authorId].toString(),
    rating = this[Reviews.rating].toInt(),
    comment = this[Reviews.comment],
    createdAt = this[Reviews.createdAt].toInstant2(),
    author = author,
)

fun ResultRow.toChargingSession(): ChargingSession = ChargingSession(
    id = this[ChargingSessions.id].toString(),
    bookingId = this[ChargingSessions.bookingId]?.toString(),
    ocppId = this[ChargingSessions.ocppId],
    transactionId = this[ChargingSessions.transactionId],
    meterStart = this[ChargingSessions.meterStart],
    meterStop = this[ChargingSessions.meterStop],
    startedAt = this[ChargingSessions.startedAt]?.toInstant2(),
    stoppedAt = this[ChargingSessions.stoppedAt]?.toInstant2(),
    stopReason = this[ChargingSessions.stopReason],
    createdAt = this[ChargingSessions.createdAt].toInstant2(),
)
