package app.chargeshare.server

import app.chargeshare.server.auth.AppleJwksTokenVerifier
import app.chargeshare.server.auth.AppleTokenVerifier
import app.chargeshare.server.auth.DisabledAppleTokenVerifier
import app.chargeshare.server.auth.TokenService
import app.chargeshare.server.db.Db
import app.chargeshare.server.db.DevSeed
import app.chargeshare.server.mail.Mailer
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.payments.clientPaymentConfig
import app.chargeshare.server.services.AuthService
import app.chargeshare.server.services.BookingService
import app.chargeshare.server.services.CustomerService
import app.chargeshare.server.services.FavoriteService
import app.chargeshare.server.services.HostService
import app.chargeshare.server.geocoding.Geocoder
import app.chargeshare.server.services.GeocodeService
import app.chargeshare.server.services.PasswordResetService
import app.chargeshare.server.services.PhotoService
import app.chargeshare.server.storage.FileSystemPhotoStorage
import app.chargeshare.server.storage.PhotoStorage
import java.nio.file.Path
import app.chargeshare.server.services.PaymentService
import app.chargeshare.server.services.PayoutService
import app.chargeshare.server.services.PayoutSweeper
import app.chargeshare.server.services.PendingBookingSweeper
import app.chargeshare.server.services.ReceiptService
import app.chargeshare.server.services.ReviewService
import app.chargeshare.server.services.SavedCardService
import app.chargeshare.server.services.WallboxService
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.chargeshare.server.config.AppConfig
import app.chargeshare.server.db.DatabaseFactory
import app.chargeshare.server.plugins.RateLimiter
import app.chargeshare.server.plugins.configureHttp
import app.chargeshare.server.plugins.configureWebSockets
import app.chargeshare.server.ocpp.OcppHandler
import app.chargeshare.server.ocpp.ocppRoutes
import app.chargeshare.server.plugins.configureSecurity
import app.chargeshare.server.plugins.configureSerialization
import app.chargeshare.server.plugins.configureStatusPages
import app.chargeshare.server.routes.apiV1Routes
import app.chargeshare.server.routes.healthRoutes
import app.chargeshare.server.routes.appLinkPageRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

/**
 * @param database bereits verbundene DB (Tests/Embedded); `null` = anhand [config] verbinden bzw. ohne DB laufen
 */
fun Application.module(
    config: AppConfig = AppConfig.fromEnv(),
    database: Database? = null,
    payments: PaymentProvider = PaymentProvider.from(config),
    apple: AppleTokenVerifier = config.appleBundleId?.takeIf { it.isNotBlank() }
        ?.let { AppleJwksTokenVerifier(it) } ?: DisabledAppleTokenVerifier,
    mailer: Mailer = Mailer.from(config),
    photoStorage: PhotoStorage = FileSystemPhotoStorage(Path.of(config.photoStorageDir)),
    geocoder: Geocoder = Geocoder.from(config),
) {
    val db = database ?: DatabaseFactory.connect(config)
    val tokens = TokenService(config)
    if (db != null && config.devSeed && !config.isProduction) DevSeed.run(Db(db), config.bcryptCost)
    val services = db?.let { buildServices(config, Db(it), tokens, payments, apple, mailer, photoStorage, geocoder) }

    configureWebSockets()
    configureSerialization()
    configureHttp()
    configureStatusPages()
    configureSecurity(tokens)

    routing {
        healthRoutes {
            if (db == null) "disabled" else runCatching {
                transaction(db) { exec("SELECT 1") }
                "ok"
            }.getOrDefault("down")
        }
        if (db != null) ocppRoutes(OcppHandler(Db(db)))
        val authLimiter = if (config.authRateLimitPerWindow > 0) {
            RateLimiter(config.authRateLimitPerWindow, config.authRateLimitWindowSeconds.seconds)
        } else {
            null
        }
        appLinkPageRoutes()
        apiV1Routes(services, authLimiter, config.trustProxyHeaders)
    }
}

private fun Application.buildServices(
    config: AppConfig,
    db: Db,
    tokens: TokenService,
    payments: PaymentProvider,
    apple: AppleTokenVerifier,
    mailer: Mailer,
    photoStorage: PhotoStorage,
    geocoder: Geocoder,
): Services {
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    monitor.subscribe(io.ktor.server.application.ApplicationStopping) { background.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    val payouts = PayoutService(db, payments)
    if (payments.requiresPayoutAccount) PayoutSweeper(payouts, PAYOUT_SWEEP_INTERVAL).start(background)
    val customers = CustomerService(db, payments)
    val bookings = BookingService(db, payments, config.stripePlatformFeePercent, payouts, customers)
    if (config.pendingBookingTtlMinutes > 0) {
        PendingBookingSweeper(bookings, config.pendingBookingTtlMinutes.minutes, config.pendingBookingSweepSeconds.seconds).start(background)
    }
    val wallboxes = WallboxService(db, payouts)
    val auth = AuthService(db, tokens, config, payments, apple, background)
    return Services(
        auth = auth,
        passwordReset = PasswordResetService(db, auth, mailer, config.passwordResetTtlMinutes.minutes, config.publicBaseUrl),
        wallboxes = wallboxes,
        photos = PhotoService(wallboxes, photoStorage, config.photoMaxUploadBytes),
        photoUploadLimit = config.photoMaxUploadBytes,
        geocoding = GeocodeService(geocoder),
        reviews = ReviewService(db),
        bookings = bookings,
        host = HostService(db, bookings),
        favorites = FavoriteService(db),
        paymentsSvc = PaymentService(db, payments, payouts, config),
        savedCards = SavedCardService(payments, customers),
        receipts = ReceiptService(db, config.stripePlatformFeePercent),
        payments = payments,
        paymentConfig = clientPaymentConfig(config, payments),
    )
}

/** So oft werden Hosts mit unvollstaendigem Onboarding bei Stripe neu geprueft. */
private val PAYOUT_SWEEP_INTERVAL = 2.minutes
