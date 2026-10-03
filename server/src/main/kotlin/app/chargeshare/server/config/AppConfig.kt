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
) {
    val isProduction: Boolean get() = env.equals("production", ignoreCase = true)

    init {
        if (isProduction) {
            require(jwtSecret != DEV_JWT_SECRET && jwtSecret.length >= 32) { "JWT_SECRET muss in Produktion gesetzt sein (>= 32 Zeichen)" }
            require(jwtRefreshSecret != DEV_JWT_REFRESH_SECRET) { "JWT_REFRESH_SECRET muss in Produktion gesetzt sein" }
        }
    }

    companion object {
        const val DEV_JWT_SECRET = "dev-only-insecure-access-secret-change-me-0123456789"
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
        )
    }
}
