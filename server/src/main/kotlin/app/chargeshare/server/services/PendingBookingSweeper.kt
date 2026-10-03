package app.chargeshare.server.services

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Hintergrund-Job: storniert regelmaessig unbezahlte Buchungen, die laenger als [ttl] offen sind (siehe [BookingService.expireUnpaid]).
 * Ein fehlgeschlagener Durchlauf beendet den Job nicht; er wird beim naechsten Intervall wiederholt.
 */
class PendingBookingSweeper(
    private val bookings: BookingService,
    private val ttl: Duration,
    private val interval: Duration,
) {
    private val log = LoggerFactory.getLogger(PendingBookingSweeper::class.java)

    /** Startet die Schleife im [scope]; sie endet, wenn der Scope abgebrochen wird. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            delay(interval)
            try {
                bookings.expireUnpaid(ttl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("sweeping unpaid bookings failed", e)
            }
        }
    }
}
