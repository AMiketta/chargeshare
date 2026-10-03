package app.chargeshare.server

import app.chargeshare.server.services.AuthService
import app.chargeshare.server.services.ReviewService
import app.chargeshare.server.services.WallboxService

/** Alle Fachdienste; wird in [module] einmal aufgebaut und an die Routen gereicht. */
class Services(
    val auth: AuthService,
    val wallboxes: WallboxService,
    val reviews: ReviewService,
)
