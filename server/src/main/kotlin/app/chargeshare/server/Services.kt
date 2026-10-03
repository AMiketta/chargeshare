package app.chargeshare.server

import app.chargeshare.server.services.AuthService

/** Alle Fachdienste; wird in [module] einmal aufgebaut und an die Routen gereicht. */
class Services(
    val auth: AuthService,
)
