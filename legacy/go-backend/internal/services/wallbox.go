package services

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"

	"github.com/chargeschare/backend/internal/models"
	"github.com/chargeschare/backend/pkg/apperror"
)

// WallboxService handles wallbox business logic.
type WallboxService struct {
	db  *pgxpool.Pool
	log *zap.Logger
}

// NewWallboxService creates a new WallboxService.
func NewWallboxService(db *pgxpool.Pool, log *zap.Logger) *WallboxService {
	return &WallboxService{db: db, log: log}
}

// GetAvailableSlots computes available 30-minute time slots for a given wallbox on a specific date,
// based on weekly availability schedule and existing bookings.
func (s *WallboxService) GetAvailableSlots(ctx context.Context, wallboxID uuid.UUID, date time.Time) ([]models.TimeSlot, error) {
	// Fetch wallbox availability config.
	var wb models.Wallbox
	row := s.db.QueryRow(ctx,
		`SELECT availability FROM wallboxes WHERE id = $1`,
		wallboxID,
	)
	var availJSON []byte
	if err := row.Scan(&availJSON); err != nil {
		return nil, apperror.NotFound("wallbox not found")
	}
	if err := wb.Availability.Scan(availJSON); err != nil {
		return nil, apperror.Internal("invalid availability data")
	}

	// Get weekday name.
	weekday := date.Weekday().String() // "Monday", "Tuesday", ...
	dayKey := fmt.Sprintf("%s", weekday)
	_ = dayKey

	// Find configured slots for this weekday (case-insensitive key lookup).
	var configSlots []models.TimeSlot
	for k, v := range wb.Availability {
		if matchDay(k, weekday) {
			configSlots = v
			break
		}
	}
	if len(configSlots) == 0 {
		return []models.TimeSlot{}, nil
	}

	// Fetch existing bookings that overlap with this date.
	dayStart := time.Date(date.Year(), date.Month(), date.Day(), 0, 0, 0, 0, time.UTC)
	dayEnd := dayStart.Add(24 * time.Hour)

	rows, err := s.db.Query(ctx,
		`SELECT start_time, end_time FROM bookings
		  WHERE wallbox_id = $1
		    AND status NOT IN ('cancelled','failed')
		    AND start_time < $3
		    AND end_time > $2`,
		wallboxID, dayStart, dayEnd,
	)
	if err != nil {
		return nil, fmt.Errorf("fetch bookings: %w", err)
	}
	defer rows.Close()

	type interval struct{ start, end time.Time }
	var booked []interval
	for rows.Next() {
		var iv interval
		if err := rows.Scan(&iv.start, &iv.end); err != nil {
			continue
		}
		booked = append(booked, iv)
	}

	// Build 30-minute candidate slots from configuration.
	var available []models.TimeSlot
	for _, cfgSlot := range configSlots {
		slotStart, err := time.Parse("15:04", cfgSlot.Start)
		if err != nil {
			continue
		}
		slotEnd, err := time.Parse("15:04", cfgSlot.End)
		if err != nil {
			continue
		}

		cursor := time.Date(date.Year(), date.Month(), date.Day(),
			slotStart.Hour(), slotStart.Minute(), 0, 0, time.UTC)
		endOfSlot := time.Date(date.Year(), date.Month(), date.Day(),
			slotEnd.Hour(), slotEnd.Minute(), 0, 0, time.UTC)

		for cursor.Add(30 * time.Minute).Before(endOfSlot) || cursor.Add(30*time.Minute).Equal(endOfSlot) {
			next := cursor.Add(30 * time.Minute)
			// Check if this candidate overlaps any booked interval.
			overlap := false
			for _, bk := range booked {
				if cursor.Before(bk.end) && next.After(bk.start) {
					overlap = true
					break
				}
			}
			if !overlap {
				available = append(available, models.TimeSlot{
					Start: cursor.Format("15:04"),
					End:   next.Format("15:04"),
				})
			}
			cursor = next
		}
	}
	return available, nil
}

// matchDay checks if a weekday key from availability config matches the given weekday string.
func matchDay(key, weekday string) bool {
	if len(key) == 0 || len(weekday) == 0 {
		return false
	}
	// Case-insensitive prefix match ("mon" == "Monday")
	k := toLower(key)
	w := toLower(weekday)
	if len(k) > len(w) {
		return false
	}
	return w[:len(k)] == k
}

func toLower(s string) string {
	b := make([]byte, len(s))
	for i := range s {
		c := s[i]
		if c >= 'A' && c <= 'Z' {
			c += 32
		}
		b[i] = c
	}
	return string(b)
}
