package app.chargeshare.shared.model

import app.chargeshare.shared.util.InstantAsStringSerializer
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Go: `models.Review`. */
@Serializable
data class Review(
    val id: String,
    @SerialName("booking_id") val bookingId: String,
    @SerialName("wallbox_id") val wallboxId: String,
    @SerialName("author_id") val authorId: String,
    /** 1..5 */
    val rating: Int,
    val comment: String? = null,
    @SerialName("created_at") @Serializable(InstantAsStringSerializer::class) val createdAt: Instant,
    val author: User? = null,
)

/** Go: `models.CreateReviewRequest` (Bewertung nur fuer abgeschlossene Buchungen). */
@Serializable
data class CreateReviewRequest(
    @SerialName("booking_id") val bookingId: String,
    val rating: Int,
    val comment: String? = null,
)

/** Go: `models.ReviewListResponse`. */
@Serializable
data class ReviewListResponse(
    val reviews: List<Review> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)
