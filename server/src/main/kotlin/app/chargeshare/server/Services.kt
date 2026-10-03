package app.chargeshare.server

import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.services.AuthService
import app.chargeshare.server.services.BookingService
import app.chargeshare.server.services.HostService
import app.chargeshare.server.services.PaymentService
import app.chargeshare.server.services.ReviewService
import app.chargeshare.server.services.WallboxService
import app.chargeshare.shared.model.PaymentConfig

/** Alle Fachdienste; wird in [module] einmal aufgebaut und an die Routen gereicht. */
class Services(
    val auth: AuthService,
    val wallboxes: WallboxService,
    val reviews: ReviewService,
    val bookings: BookingService,
    val host: HostService,
    val paymentsSvc: PaymentService,
    val payments: PaymentProvider,
    /** Öffentliche Zahlungskonfiguration für die Apps (ohne Secrets). */
    val paymentConfig: PaymentConfig,
)
