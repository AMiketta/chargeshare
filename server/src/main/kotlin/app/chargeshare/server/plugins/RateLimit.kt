package app.chargeshare.server.plugins

import app.chargeshare.server.util.tooManyRequests
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.plugins.origin
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.time.Duration

/** Ergebnis einer Prüfung durch [RateLimiter]. */
data class RateLimitDecision(val allowed: Boolean, val retryAfterSeconds: Long)

/**
 * Einfaches Rate-Limiting im Arbeitsspeicher (festes Zeitfenster je Schlüssel, z. B. Client-Adresse).
 *
 * Gilt nur für diese Server-Instanz (kein gemeinsamer Zustand zwischen mehreren Instanzen) und geht beim Neustart verloren.
 * Der Speicher ist begrenzt: abgelaufene Fenster werden entfernt; ist die Tabelle trotzdem voll, werden unbekannte Schlüssel
 * nicht mehr gezählt (der Dienst bleibt für alle erreichbar, statt Nutzer auszusperren).
 *
 * @param limit erlaubte Versuche pro Fenster (> 0)
 * @param nowMillis Zeitquelle in Millisekunden (für Tests austauschbar)
 */
class RateLimiter(
    private val limit: Int,
    private val window: Duration,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxKeys: Int = DEFAULT_MAX_KEYS,
) {
    private class Bucket(val windowStart: Long, var count: Int)

    private val buckets = HashMap<String, Bucket>()
    private var lastPurge = nowMillis()

    init {
        require(limit > 0) { "limit muss größer als 0 sein" }
        require(window.isPositive()) { "window muss positiv sein" }
    }

    /** Zählt einen Versuch für [key] und sagt, ob er erlaubt ist. */
    @Synchronized
    fun tryAcquire(key: String): RateLimitDecision {
        val now = nowMillis()
        val windowMillis = window.inWholeMilliseconds
        if (now - lastPurge >= windowMillis) purgeExpired(now, windowMillis)
        var bucket = buckets[key]
        if (bucket == null || now - bucket.windowStart >= windowMillis) {
            if (bucket == null && buckets.size >= maxKeys) return RateLimitDecision(allowed = true, retryAfterSeconds = 0)
            bucket = Bucket(now, 0)
            buckets[key] = bucket
        }
        if (bucket.count >= limit) {
            val remaining = bucket.windowStart + windowMillis - now
            return RateLimitDecision(allowed = false, retryAfterSeconds = (remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)
        }
        bucket.count++
        return RateLimitDecision(allowed = true, retryAfterSeconds = 0)
    }

    private fun purgeExpired(now: Long, windowMillis: Long) {
        buckets.values.removeAll { now - it.windowStart >= windowMillis }
        lastPurge = now
    }

    private companion object {
        const val DEFAULT_MAX_KEYS = 100_000
        const val MILLIS_PER_SECOND = 1000L
    }
}

/** Konfiguration von [RateLimitPlugin]. */
class RateLimitConfig {
    lateinit var limiter: RateLimiter
    var trustProxyHeaders: Boolean = false
}

/** Antwortet mit `429` und `Retry-After`, sobald eine Client-Adresse das Limit des [RateLimiter]s überschreitet. */
val RateLimitPlugin = createRouteScopedPlugin("RateLimit", ::RateLimitConfig) {
    val limiter = pluginConfig.limiter
    val trustProxy = pluginConfig.trustProxyHeaders
    onCall { call ->
        val decision = limiter.tryAcquire(clientKey(call, trustProxy))
        if (!decision.allowed) {
            call.response.headers.append(HttpHeaders.RetryAfter, decision.retryAfterSeconds.toString())
            throw tooManyRequests("too many attempts, please try again later")
        }
    }
}

/** Client-Adresse als Schlüssel; hinter einem vertrauenswürdigen Proxy der letzte `X-Forwarded-For`-Eintrag. */
internal fun clientKey(call: ApplicationCall, trustProxyHeaders: Boolean): String {
    if (trustProxyHeaders) {
        call.request.headers["X-Forwarded-For"]?.split(',')?.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return call.request.origin.remoteAddress
}

/** `POST [path]` mit Rate-Limit; ohne [limiter] (Limit abgeschaltet) eine normale Route. */
fun Route.limitedPost(
    path: String,
    limiter: RateLimiter?,
    trustProxyHeaders: Boolean,
    body: suspend RoutingContext.() -> Unit,
) {
    route(path) {
        if (limiter != null) {
            install(RateLimitPlugin) {
                this.limiter = limiter
                this.trustProxyHeaders = trustProxyHeaders
            }
        }
        post(body)
    }
}
