package app.chargeshare.server.db

import app.chargeshare.server.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

/** Verbindet PostgreSQL (Hikari), fuehrt Flyway-Migrationen aus und initialisiert Exposed. */
object DatabaseFactory {

    fun createDataSource(url: String, user: String? = null, password: String? = null): DataSource =
        HikariDataSource(HikariConfig().apply {
            // Go-Format `postgres://user:pw@host/db` -> JDBC
            jdbcUrl = toJdbcUrl(url)
            user?.let { username = it }
            password?.let { this.password = it }
            maximumPoolSize = 10
            driverClassName = "org.postgresql.Driver"
        })

    /** Wendet alle `classpath:db/migration`-Skripte an (V1__init.sql = Go `000001_init.up.sql`). */
    fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    fun connect(config: AppConfig): Database? {
        val url = config.databaseUrl ?: return null
        val ds = createDataSource(url, config.databaseUser, config.databasePassword)
        migrate(ds)
        return Database.connect(ds)
    }

    fun toJdbcUrl(url: String): String = when {
        url.startsWith("jdbc:") -> url
        url.startsWith("postgres://") -> "jdbc:postgresql://" + url.removePrefix("postgres://")
        url.startsWith("postgresql://") -> "jdbc:postgresql://" + url.removePrefix("postgresql://")
        else -> url
    }
}
