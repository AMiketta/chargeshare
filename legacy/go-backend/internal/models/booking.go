package models

import (
	"time"

	"github.com/google/uuid"
)

// BookingStatus represents the lifecycle state of a booking.
type BookingStatus string

const (
	BookingPending   BookingStatus = "pending"
	BookingConfirmed BookingStatus = "confirmed"
	BookingActive    BookingStatus = "active"
	BookingCompleted BookingStatus = "completed"
	BookingCancelled BookingStatus = "cancelled"
	BookingFailed    BookingStatus = "failed"
)

// Booking represents a driver's reservation of a wallbox.
type Booking struct {
	ID                     uuid.UUID     `json:"id"                        db:"id"`
	WallboxID              uuid.UUID     `json:"wallbox_id"                db:"wallbox_id"`
	DriverID               uuid.UUID     `json:"driver_id"                 db:"driver_id"`
	StartTime              time.Time     `json:"start_time"                db:"start_time"`
	EndTime                time.Time     `json:"end_time"                  db:"end_time"`
	Status                 BookingStatus `json:"status"                    db:"status"`
	EstimatedKwh           *float64      `json:"estimated_kwh,omitempty"   db:"estimated_kwh"`
	ActualKwh              *float64      `json:"actual_kwh,omitempty"      db:"actual_kwh"`
	PricePerKwh            float64       `json:"price_per_kwh"             db:"price_per_kwh"`
	TotalAmount            *float64      `json:"total_amount,omitempty"    db:"total_amount"`
	StripePaymentIntentID  *string       `json:"stripe_payment_intent_id,omitempty" db:"stripe_payment_intent_id"`
	StripeChargeID         *string       `json:"stripe_charge_id,omitempty"         db:"stripe_charge_id"`
	OcppTransactionID      *int          `json:"ocpp_transaction_id,omitempty"      db:"ocpp_transaction_id"`
	CancelReason           *string       `json:"cancel_reason,omitempty"            db:"cancel_reason"`
	CreatedAt              time.Time     `json:"created_at"                db:"created_at"`
	UpdatedAt              time.Time     `json:"updated_at"                db:"updated_at"`

	// Eager-loaded relations (not in DB columns)
	Wallbox *Wallbox `json:"wallbox,omitempty" db:"-"`
	Driver  *User    `json:"driver,omitempty"  db:"-"`
}

// CreateBookingRequest is the payload for creating a booking.
type CreateBookingRequest struct {
	WallboxID string `json:"wallbox_id" validate:"required,uuid"`
	StartTime string `json:"start_time" validate:"required"` // RFC3339
	EndTime   string `json:"end_time"   validate:"required"` // RFC3339
}

// CancelBookingRequest is the payload for cancelling a booking.
type CancelBookingRequest struct {
	Reason *string `json:"reason,omitempty"`
}

// BookingResponse wraps a booking with optional Stripe client secret.
type BookingResponse struct {
	Booking      *Booking `json:"booking"`
	ClientSecret *string  `json:"client_secret,omitempty"`
}

// ListBookingsFilter holds query parameters for listing bookings.
type ListBookingsFilter struct {
	Status *BookingStatus
	Limit  int
	Offset int
}
