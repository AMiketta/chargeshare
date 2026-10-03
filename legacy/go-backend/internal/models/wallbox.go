package models

import (
	"encoding/json"
	"time"

	"github.com/google/uuid"
)

// ConnectorType represents the type of charging connector.
type ConnectorType string

const (
	ConnectorType2   ConnectorType = "type2"
	ConnectorTypeCCS ConnectorType = "ccs"
	ConnectorCHAdeMO ConnectorType = "chademo"
	ConnectorType1   ConnectorType = "type1"
)

// WeeklyAvailability maps weekday names to time slots.
// Example: {"monday": [{"start": "08:00", "end": "20:00"}]}
type WeeklyAvailability map[string][]TimeSlot

// TimeSlot represents a time window within a day.
type TimeSlot struct {
	Start string `json:"start"` // "HH:MM"
	End   string `json:"end"`   // "HH:MM"
}

// Wallbox represents a charging station owned by a host.
type Wallbox struct {
	ID             uuid.UUID           `json:"id"              db:"id"`
	OwnerID        uuid.UUID           `json:"owner_id"        db:"owner_id"`
	Name           string              `json:"name"            db:"name"`
	Address        string              `json:"address"         db:"address"`
	City           string              `json:"city"            db:"city"`
	PostalCode     string              `json:"postal_code"     db:"postal_code"`
	Lat            float64             `json:"lat"             db:"lat"`
	Lng            float64             `json:"lng"             db:"lng"`
	PowerKW        float64             `json:"power_kw"        db:"power_kw"`
	ConnectorType  ConnectorType       `json:"connector_type"  db:"connector_type"`
	PricePerKwh    float64             `json:"price_per_kwh"   db:"price_per_kwh"`
	MinDurationMin int                 `json:"min_duration_min" db:"min_duration_min"`
	MaxDurationMin int                 `json:"max_duration_min" db:"max_duration_min"`
	IsOnline       bool                `json:"is_online"       db:"is_online"`
	IsMetered      bool                `json:"is_metered"      db:"is_metered"`
	OcppID         *string             `json:"ocpp_id,omitempty" db:"ocpp_id"`
	Model          *string             `json:"model,omitempty"   db:"model"`
	Manufacturer   *string             `json:"manufacturer,omitempty" db:"manufacturer"`
	Photos         []string            `json:"photos,omitempty"  db:"photos"`
	Availability   WeeklyAvailability  `json:"availability"    db:"availability"`
	RatingAvg      float64             `json:"rating_avg"      db:"rating_avg"`
	RatingCount    int                 `json:"rating_count"    db:"rating_count"`
	CreatedAt      time.Time           `json:"created_at"      db:"created_at"`
	UpdatedAt      time.Time           `json:"updated_at"      db:"updated_at"`

	// Computed field: distance in meters (not stored in DB)
	Distance *float64 `json:"distance_m,omitempty" db:"-"`
}

// AvailabilityJSON is a helper for scanning JSONB availability from DB.
type AvailabilityJSON struct {
	WeeklyAvailability
}

func (a *WeeklyAvailability) Scan(src interface{}) error {
	var data []byte
	switch v := src.(type) {
	case []byte:
		data = v
	case string:
		data = []byte(v)
	default:
		*a = make(WeeklyAvailability)
		return nil
	}
	return json.Unmarshal(data, a)
}

// CreateWallboxRequest is the payload for creating a wallbox.
type CreateWallboxRequest struct {
	Name           string             `json:"name"            validate:"required,min=1,max=200"`
	Address        string             `json:"address"         validate:"required"`
	City           string             `json:"city"            validate:"required"`
	PostalCode     string             `json:"postal_code"     validate:"required"`
	Lat            float64            `json:"lat"             validate:"required,min=-90,max=90"`
	Lng            float64            `json:"lng"             validate:"required,min=-180,max=180"`
	PowerKW        float64            `json:"power_kw"        validate:"required,gt=0"`
	ConnectorType  ConnectorType      `json:"connector_type"  validate:"required,oneof=type2 ccs chademo type1"`
	PricePerKwh    float64            `json:"price_per_kwh"   validate:"required,gt=0"`
	MinDurationMin *int               `json:"min_duration_min,omitempty"`
	MaxDurationMin *int               `json:"max_duration_min,omitempty"`
	IsMetered      bool               `json:"is_metered"`
	OcppID         *string            `json:"ocpp_id,omitempty"`
	Model          *string            `json:"model,omitempty"`
	Manufacturer   *string            `json:"manufacturer,omitempty"`
	Photos         []string           `json:"photos,omitempty"`
	Availability   WeeklyAvailability `json:"availability"`
}

// UpdateWallboxRequest is the payload for updating a wallbox.
type UpdateWallboxRequest struct {
	Name           *string            `json:"name,omitempty"            validate:"omitempty,min=1,max=200"`
	Address        *string            `json:"address,omitempty"`
	City           *string            `json:"city,omitempty"`
	PostalCode     *string            `json:"postal_code,omitempty"`
	Lat            *float64           `json:"lat,omitempty"             validate:"omitempty,min=-90,max=90"`
	Lng            *float64           `json:"lng,omitempty"             validate:"omitempty,min=-180,max=180"`
	PowerKW        *float64           `json:"power_kw,omitempty"        validate:"omitempty,gt=0"`
	ConnectorType  *ConnectorType     `json:"connector_type,omitempty"  validate:"omitempty,oneof=type2 ccs chademo type1"`
	PricePerKwh    *float64           `json:"price_per_kwh,omitempty"   validate:"omitempty,gt=0"`
	MinDurationMin *int               `json:"min_duration_min,omitempty"`
	MaxDurationMin *int               `json:"max_duration_min,omitempty"`
	IsMetered      *bool              `json:"is_metered,omitempty"`
	OcppID         *string            `json:"ocpp_id,omitempty"`
	Model          *string            `json:"model,omitempty"`
	Manufacturer   *string            `json:"manufacturer,omitempty"`
	Photos         []string           `json:"photos,omitempty"`
	Availability   WeeklyAvailability `json:"availability,omitempty"`
}

// WallboxStatusRequest is the payload for toggling wallbox online status.
type WallboxStatusRequest struct {
	IsOnline bool `json:"is_online"`
}

// WallboxListFilter holds query parameters for listing/searching wallboxes.
type WallboxListFilter struct {
	Lat           *float64
	Lng           *float64
	RadiusM       float64
	ConnectorType *string
	MinPowerKW    *float64
	MaxPriceKwh   *float64
	AvailableFrom *time.Time
	AvailableTo   *time.Time
	Limit         int
	Offset        int
}
