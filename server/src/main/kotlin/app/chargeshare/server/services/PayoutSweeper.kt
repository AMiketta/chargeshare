package app.chargeshare.server.services

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/** Hintergrund-Job: schaltet Hosts frei, die ihr Stripe-Onboarding inzwischen abgeschlossen haben (siehe [PayoutService.refreshPending]). */
class PayoutSweeper(private val payouts: PayoutService, private val interval: Duration) {
    private val log = LoggerFactory.getLogger(PayoutSweeper::class.java)

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            delay(interval)
            try {
                val released = payouts.refreshPending()
                if (released > 0) log.info("{} host(s) completed payout onboarding", released)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("sweeping payout onboarding failed", e)
            }
        }
    }
}
