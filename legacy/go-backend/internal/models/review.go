package models

import (
	"time"

	"github.com/google/uuid"
)

// Review represents a driver's review of a wallbox after a completed booking.
type Review struct {
	ID        uuid.UUID `json:"id"          db:"id"`
	BookingID uuid.UUID `json:"booking_id"  db:"booking_id"`
	WallboxID uuid.UUID `json:"wallbox_id"  db:"wallbox_id"`
	AuthorID  uuid.UUID `json:"author_id"   db:"author_id"`
	Rating    int16     `json:"rating"      db:"rating"`
	Comment   *string   `json:"comment,omitempty" db:"comment"`
	CreatedAt time.Time `json:"created_at"  db:"created_at"`

	// Eager-loaded
	Author *User `json:"author,omitempty" db:"-"`
}

// CreateReviewRequest is the payload for submitting a review.
type CreateReviewRequest struct {
	BookingID string  `json:"booking_id" validate:"required,uuid"`
	Rating    int16   `json:"rating"     validate:"required,min=1,max=5"`
	Comment   *string `json:"comment,omitempty" validate:"omitempty,max=2000"`
}

// ReviewListResponse wraps paginated reviews.
type ReviewListResponse struct {
	Reviews    []*Review `json:"reviews"`
	Total      int       `json:"total"`
	Limit      int       `json:"limit"`
	Offset     int       `json:"offset"`
}
