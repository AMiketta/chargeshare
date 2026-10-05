package app.chargeshare.server

import app.chargeshare.server.config.AppConfig
import app.chargeshare.server.db.DatabaseFactory
import app.chargeshare.server.db.Users
import app.chargeshare.server.db.Wallboxes
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.sql.Statement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Fuehrt die Flyway-Migration (V1__init.sql, 1:1 aus dem Go-Backend) gegen ein echtes, eingebettetes
 * PostgreSQL aus – inkl. Extensions (cube, earthdistance, btree_gist) und Exclusion-Constraint.
 */
class MigrationTest {

    @Test
    fun migrationCreatesSchemaAndServerReportsDatabaseOk() {
        EmbeddedPostgres.builder().start().use { pg ->
            val ds = pg.postgresDatabase
            DatabaseFactory.migrate(ds)
            val db = Database.connect(ds)

            // Tabellen vorhanden
            val tables = mutableListOf<String>()
            ds.connection.use { c ->
                c.createStatement().use { st: Statement ->
                    st.executeQuery("select table_name from information_schema.tables where table_schema='public'").use { rs ->
                        while (rs.next()) tables += rs.getString(1)
                    }
                }
            }
            for (t in listOf("users", "wallboxes", "bookings", "reviews", "charging_sessions", "refresh_tokens", "favorites", "push_devices")) {
                assertTrue(t in tables, "Tabelle $t fehlt (vorhanden: $tables)")
            }

            // Exposed-Tabellendefinitionen passen zum Schema
            transaction(db) {
                assertEquals(0L, Users.selectAll().count())
                assertEquals(0L, Wallboxes.selectAll().count())
            }

            // Doppelbuchung wird vom Exclusion-Constraint verhindert
            ds.connection.use { c ->
                c.createStatement().use { st ->
                    st.execute("insert into users(id,email,name) values ('00000000-0000-0000-0000-000000000001','a@b.de','A')")
                    st.execute("""insert into wallboxes(id,owner_id,name,address,city,postal_code,lat,lng,power_kw,connector_type,price_per_kwh)
                        values ('00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-000000000001','W','S 1','Bremen','28195',53.07,8.8,11,'type2',0.39)""")
                    st.execute("""insert into bookings(wallbox_id,driver_id,start_time,end_time,price_per_kwh)
                        values ('00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-000000000001','2026-05-01T10:00Z','2026-05-01T12:00Z',0.39)""")
                    val overlap = runCatching {
                        st.execute("""insert into bookings(wallbox_id,driver_id,start_time,end_time,price_per_kwh)
                            values ('00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-000000000001','2026-05-01T11:00Z','2026-05-01T13:00Z',0.39)""")
                    }
                    assertTrue(overlap.isFailure, "ueberlappende Buchung haette abgelehnt werden muessen")
                }
            }

            // /health meldet DB als ok
            testApplication {
                application { module(AppConfig(), db) }
                assertEquals("""{"status":"ok","database":"ok"}""", client.get("/health").bodyAsText())
            }
        }
    }
}
