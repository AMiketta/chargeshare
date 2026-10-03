package models

import (
	"time"

	"github.com/google/uuid"
)

// ChargingSession stores OCPP telemetry for a charging event.
type ChargingSession struct {
	ID            uuid.UUID  `json:"id"             db:"id"`
	BookingID     *uuid.UUID `json:"booking_id,omitempty" db:"booking_id"`
	OcppID        string     `json:"ocpp_id"        db:"ocpp_id"`
	TransactionID *int       `json:"transaction_id,omitempty" db:"transaction_id"`
	MeterStart    *int       `json:"meter_start,omitempty"   db:"meter_start"`
	MeterStop     *int       `json:"meter_stop,omitempty"    db:"meter_stop"`
	StartedAt     *time.Time `json:"started_at,omitempty"    db:"started_at"`
	StoppedAt     *time.Time `json:"stopped_at,omitempty"    db:"stopped_at"`
	StopReason    *string    `json:"stop_reason,omitempty"   db:"stop_reason"`
	CreatedAt     time.Time  `json:"created_at"     db:"created_at"`

	// Eager-loaded
	Booking *Booking `json:"booking,omitempty" db:"-"`
	Driver  *User    `json:"driver,omitempty"  db:"-"`
}

// RefreshToken stores a hashed refresh token in the DB.
type RefreshToken struct {
	ID        uuid.UUID `db:"id"`
	UserID    uuid.UUID `db:"user_id"`
	TokenHash string    `db:"token_hash"`
	ExpiresAt time.Time `db:"expires_at"`
	CreatedAt time.Time `db:"created_at"`
}

// HostEarnings summarises earnings for a host.
type HostEarnings struct {
	Total      float64 `json:"total"`
	Month      float64 `json:"month"`
	Sessions   int     `json:"sessions"`
	AvgRating  float64 `json:"avg_rating"`
}
