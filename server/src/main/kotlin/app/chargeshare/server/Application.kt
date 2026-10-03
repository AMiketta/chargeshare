package app.chargeshare.server

import app.chargeshare.server.auth.TokenService
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
fun Application.module(config: AppConfig = AppConfig.fromEnv(), database: Database? = null) {
    val db = database ?: DatabaseFactory.connect(config)
    val tokens = TokenService(config)

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
        apiV1Routes()
    }
}
