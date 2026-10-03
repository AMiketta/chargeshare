package app.chargeshare.server

import app.chargeshare.server.auth.AppleJwksTokenVerifier
import app.chargeshare.server.auth.AppleTokenVerifier
import app.chargeshare.server.auth.DisabledAppleTokenVerifier
import app.chargeshare.server.auth.TokenService
import app.chargeshare.server.db.Db
import app.chargeshare.server.db.DevSeed
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.services.AuthService
import app.chargeshare.server.services.BookingService
import app.chargeshare.server.services.HostService
import app.chargeshare.server.services.PaymentService
import app.chargeshare.server.services.ReviewService
import app.chargeshare.server.services.WallboxService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.chargeshare.server.config.AppConfig
import app.chargeshare.server.db.DatabaseFactory
import app.chargeshare.server.plugins.configureHttp
import app.chargeshare.server.plugins.configureSecurity
import app.chargeshare.server.plugins.configureSerialization
import app.chargeshare.server.plugins.configureStatusPages
import app.chargeshare.server.routes.apiV1Routes
import app.chargeshare.server.routes.healthRoutes
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
) {
    val db = database ?: DatabaseFactory.connect(config)
    val tokens = TokenService(config)
    if (db != null && config.devSeed && !config.isProduction) DevSeed.run(Db(db), config.bcryptCost)
    val services = db?.let { buildServices(config, Db(it), tokens, payments, apple) }

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
        apiV1Routes(services)
    }
}

private fun Application.buildServices(
    config: AppConfig,
    db: Db,
    tokens: TokenService,
    payments: PaymentProvider,
    apple: AppleTokenVerifier,
): Services {
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    monitor.subscribe(io.ktor.server.application.ApplicationStopping) { background.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    val bookings = BookingService(db, payments, config.stripePlatformFeePercent)
    return Services(
        auth = AuthService(db, tokens, config, payments, apple, background),
        wallboxes = WallboxService(db),
        reviews = ReviewService(db),
        bookings = bookings,
        host = HostService(db, bookings),
        paymentsSvc = PaymentService(db, payments),
        payments = payments,
    )
}
