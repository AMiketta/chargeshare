package app.chargeshare.server.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Duenner Wrapper: fuehrt blockierende Exposed-Transaktionen auf `Dispatchers.IO` aus. */
class Db(val database: Database) {
    suspend fun <T> tx(block: JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { transaction(database) { block() } }

    fun <T> txBlocking(block: JdbcTransaction.() -> T): T = transaction(database) { block() }
}

/** PostgreSQL-SQLSTATEs, die wir gezielt auf HTTP-Fehler abbilden. */
object PgState {
    const val UNIQUE_VIOLATION = "23505"
    const val EXCLUSION_VIOLATION = "23P01"
    const val FOREIGN_KEY_VIOLATION = "23503"
}

fun Throwable.sqlState(): String? {
    var t: Throwable? = this
    while (t != null) {
        if (t is java.sql.SQLException && t.sqlState != null) return t.sqlState
        t = t.cause
    }
    return null
}
