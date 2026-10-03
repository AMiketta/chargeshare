package app.chargeshare.server.util

import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.uuid.Uuid
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

fun OffsetDateTime.toInstant2(): Instant = this.toInstant().toKotlinInstant()

fun BigDecimal?.toDoubleOrNull2(): Double? = this?.toDouble()

fun parseUuid(value: String, what: String): Uuid =
    runCatching { Uuid.parse(value) }.getOrNull() ?: throw badRequest("invalid $what")

val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
