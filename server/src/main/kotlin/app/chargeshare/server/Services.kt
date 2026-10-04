package app.chargeshare.server

import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.services.AuthService
import app.chargeshare.server.services.BookingService
import app.chargeshare.server.services.FavoriteService
import app.chargeshare.server.services.HostService
import app.chargeshare.server.services.GeocodeService
import app.chargeshare.server.services.PasswordResetService
import app.chargeshare.server.services.PhotoService
import app.chargeshare.server.services.PaymentService
import app.chargeshare.server.services.ReceiptService
import app.chargeshare.server.services.ReviewService
import app.chargeshare.server.services.SavedCardService
import app.chargeshare.server.services.WallboxService
import app.chargeshare.shared.model.PaymentConfig

/** Alle Fachdienste; wird in [module] einmal aufgebaut und an die Routen gereicht. */
class Services(
    val auth: AuthService,
    val passwordReset: PasswordResetService,
    val wallboxes: WallboxService,
    val photos: PhotoService,
    val geocoding: GeocodeService,
    /** Obergrenze der Upload-Größe in Bytes (aus der Konfiguration). */
    val photoUploadLimit: Long,
    val reviews: ReviewService,
    val bookings: BookingService,
    val host: HostService,
    val favorites: FavoriteService,
    val paymentsSvc: PaymentService,
    val savedCards: SavedCardService,
    val receipts: ReceiptService,
    val payments: PaymentProvider,
    /** Öffentliche Zahlungskonfiguration für die Apps (ohne Secrets). */
    val paymentConfig: PaymentConfig,
)
