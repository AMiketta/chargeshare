package app.chargeshare.server.config

/**
 * Konfiguration aus Umgebungsvariablen (dieselben Namen wie im Go-Backend, siehe legacy/go-backend/.env.example).
 * Ohne `DATABASE_URL` startet der Server ohne Datenbank (nur /health und Stubs) – praktisch fuer lokale Entwicklung.
 */
data class AppConfig(
    val port: Int = 8080,
    val env: String = "development",
    val databaseUrl: String? = null,
    val databaseUser: String? = null,
    val databasePassword: String? = null,
    val jwtSecret: String = DEV_JWT_SECRET,
    val jwtRefreshSecret: String = DEV_JWT_REFRESH_SECRET,
    val jwtIssuer: String = "chargeshare",
    val accessTokenTtlSeconds: Long = 15 * 60,
    val appleBundleId: String? = null,
    val stripeSecretKey: String? = null,
    val stripeWebhookSecret: String? = null,
    val stripePlatformFeePercent: Int = 15,
    /** Oeffentlicher Stripe-Schluessel (`pk_...`); wird an die Apps ausgeliefert. Nie ein Secret Key. */
    val stripePublishableKey: String? = null,
    /** Apple-Pay-Merchant-ID (`merchant.app.chargeshare`); nur fuer iOS noetig. */
    val applePayMerchantId: String? = null,
    /** `test` oder `production`; ohne Angabe abgeleitet aus dem Publishable Key (`pk_live_` = Produktion). */
    val googlePayEnvironment: String = deriveGooglePayEnvironment(stripePublishableKey),
    /** Name des Haendlers in den Zahlungsdialogen (Google Pay / Apple Pay / PaymentSheet). */
    val merchantDisplayName: String = "ChargeShare",
    /** ISO-3166-Land des Haendlers, Pflicht fuer Google Pay und Apple Pay. */
    val merchantCountryCode: String = "DE",
    /** bcrypt-Kosten (Go-Backend: 12). Tests senken den Wert, damit sie schnell bleiben. */
    val bcryptCost: Int = 12,
    val refreshTokenTtlDays: Long = 30,
    /**
     * Nach wie vielen Minuten eine unbezahlte (`pending`) Buchung automatisch storniert wird und der Slot wieder frei ist.
     * `0` schaltet den automatischen Ablauf ab.
     */
    val pendingBookingTtlMinutes: Long = 15,
    /** Abstand der Pruefungen auf abgelaufene unbezahlte Buchungen in Sekunden. */
    val pendingBookingSweepSeconds: Long = 60,
    /**
     * Wie viele Versuche pro Fenster und Client-Adresse für Registrierung, Login und Apple-Login erlaubt sind (Go-Backend: 10 pro Minute).
     * `0` schaltet das Rate-Limiting ab.
     */
    val authRateLimitPerWindow: Int = 10,
    /** Länge des Zeitfensters für [authRateLimitPerWindow] in Sekunden. */
    val authRateLimitWindowSeconds: Long = 60,
    /**
     * Client-Adresse aus `X-Forwarded-For` lesen (letzter Eintrag = vom vorgeschalteten Proxy ergänzt). Nur aktivieren, wenn der Server
     * **ausschließlich** hinter genau einem vertrauenswürdigen Reverse-Proxy erreichbar ist; sonst lässt sich das Limit umgehen.
     */
    val trustProxyHeaders: Boolean = false,
    /** Legt beim Start Beispieldaten (Demo-Host + Wallboxen in Bremen) an. Nie in Produktion. */
    val devSeed: Boolean = false,
) {
    val isProduction: Boolean get() = env.equals("production", ignoreCase = true)
    val isDevelopment: Boolean get() = env.equals("development", ignoreCase = true)

    init {
        if (isProduction) {
            require(jwtSecret != DEV_JWT_SECRET && jwtSecret.length >= 32) { "JWT_SECRET muss in Produktion gesetzt sein (>= 32 Zeichen)" }
            require(jwtRefreshSecret != DEV_JWT_REFRESH_SECRET) { "JWT_REFRESH_SECRET muss in Produktion gesetzt sein" }
            require(!devSeed) { "devSeed ist in Produktion nicht erlaubt" }
            if (!stripeSecretKey.isNullOrBlank()) {
                require(!stripePublishableKey.isNullOrBlank()) { "STRIPE_PUBLISHABLE_KEY muss in Produktion gesetzt sein" }
            }
        }
        // Schutz vor Verwechslung: dieser Wert geht ungeschuetzt an jede App, ein Secret Key duerfte nie hier landen.
        require(stripePublishableKey.isNullOrBlank() || stripePublishableKey.startsWith(PUBLISHABLE_KEY_PREFIX)) {
            "STRIPE_PUBLISHABLE_KEY muss mit pk_ beginnen (kein Secret Key!)"
        }
        require(pendingBookingTtlMinutes >= 0) { "PENDING_BOOKING_TTL_MINUTES darf nicht negativ sein" }
        require(authRateLimitPerWindow >= 0) { "AUTH_RATE_LIMIT_PER_WINDOW darf nicht negativ sein" }
        require(authRateLimitWindowSeconds >= 1) { "AUTH_RATE_LIMIT_WINDOW_SECONDS muss mindestens 1 sein" }
        require(pendingBookingSweepSeconds >= 1) { "PENDING_BOOKING_SWEEP_SECONDS muss mindestens 1 sein" }
        require(googlePayEnvironment in GOOGLE_PAY_ENVIRONMENTS) { "GOOGLE_PAY_ENVIRONMENT muss test oder production sein" }
    }

    companion object {
        const val DEV_JWT_SECRET = "dev-only-insecure-access-secret-change-me-0123456789"
        const val PUBLISHABLE_KEY_PREFIX = "pk_"
        private const val LIVE_PUBLISHABLE_KEY_PREFIX = "pk_live_"
        private val GOOGLE_PAY_ENVIRONMENTS = setOf("test", "production")

        private fun deriveGooglePayEnvironment(publishableKey: String?): String =
            if (publishableKey?.startsWith(LIVE_PUBLISHABLE_KEY_PREFIX) == true) "production" else "test"

        const val DEV_JWT_REFRESH_SECRET = "dev-only-insecure-refresh-secret-change-me-0123456789"

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig = AppConfig(
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            env = env["ENV"] ?: "development",
            databaseUrl = env["DATABASE_URL"]?.takeIf { it.isNotBlank() },
            databaseUser = env["DATABASE_USER"],
            databasePassword = env["DATABASE_PASSWORD"],
            jwtSecret = env["JWT_SECRET"] ?: DEV_JWT_SECRET,
            jwtRefreshSecret = env["JWT_REFRESH_SECRET"] ?: DEV_JWT_REFRESH_SECRET,
            appleBundleId = env["APPLE_BUNDLE_ID"],
            stripeSecretKey = env["STRIPE_SECRET_KEY"],
            stripeWebhookSecret = env["STRIPE_WEBHOOK_SECRET"],
            stripePlatformFeePercent = env["STRIPE_PLATFORM_FEE_PERCENT"]?.toIntOrNull() ?: 15,
            stripePublishableKey = env["STRIPE_PUBLISHABLE_KEY"]?.takeIf { it.isNotBlank() },
            applePayMerchantId = env["APPLE_PAY_MERCHANT_ID"]?.takeIf { it.isNotBlank() },
            googlePayEnvironment = env["GOOGLE_PAY_ENVIRONMENT"]?.takeIf { it.isNotBlank() }?.lowercase()
                ?: deriveGooglePayEnvironment(env["STRIPE_PUBLISHABLE_KEY"]),
            merchantDisplayName = env["MERCHANT_DISPLAY_NAME"]?.takeIf { it.isNotBlank() } ?: "ChargeShare",
            merchantCountryCode = env["MERCHANT_COUNTRY_CODE"]?.takeIf { it.isNotBlank() }?.uppercase() ?: "DE",
            pendingBookingTtlMinutes = env["PENDING_BOOKING_TTL_MINUTES"]?.toLongOrNull() ?: 15,
            pendingBookingSweepSeconds = env["PENDING_BOOKING_SWEEP_SECONDS"]?.toLongOrNull() ?: 60,
            authRateLimitPerWindow = env["AUTH_RATE_LIMIT_PER_WINDOW"]?.toIntOrNull() ?: 10,
            authRateLimitWindowSeconds = env["AUTH_RATE_LIMIT_WINDOW_SECONDS"]?.toLongOrNull() ?: 60,
            trustProxyHeaders = env["TRUST_PROXY_HEADERS"].equals("true", ignoreCase = true),
            // Dev-Seed: nur ENV=development und solange DEV_SEED nicht explizit "false" ist
            devSeed = (env["ENV"] ?: "development").equals("development", ignoreCase = true) &&
                !(env["DEV_SEED"] ?: "true").equals("false", ignoreCase = true),
        )
    }
}
