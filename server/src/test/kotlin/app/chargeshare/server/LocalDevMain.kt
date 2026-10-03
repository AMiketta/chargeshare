package app.chargeshare.server

import app.chargeshare.server.config.AppConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.io.File

/**
 * Lokaler Dev-Start ohne Docker: `./gradlew :server:runLocal`.
 * Daten bleiben in `server/.local-pg` erhalten (Ordner loeschen = frische Datenbank).
 * Umgebungsvariablen (PORT, STRIPE_SECRET_KEY, ...) wirken wie beim normalen Start; DATABASE_URL wird ueberschrieben.
 */
fun main() {
    val dataDir = File("server/.local-pg").takeIf { File("server").isDirectory } ?: File(".local-pg")
    val pg = EmbeddedPostgres.builder()
        .setDataDirectory(dataDir)
        .setCleanDataDirectory(false)
        .setPort(System.getenv("LOCAL_PG_PORT")?.toIntOrNull() ?: 0)
        .start()
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { pg.close() } })
    val base = AppConfig.fromEnv()
    val config = base.copy(databaseUrl = pg.getJdbcUrl("postgres", "postgres"), databaseUser = "postgres", databasePassword = "")
    println("ChargeShare dev server: http://0.0.0.0:${config.port}  (Postgres: ${config.databaseUrl}, Dev-Seed: ${config.devSeed}, Payment: ${if (config.stripeSecretKey.isNullOrBlank()) "FAKE" else "Stripe"})")
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}
