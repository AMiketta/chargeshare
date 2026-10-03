package handlers

import (
	"net/http"
	"strconv"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"

	mw "github.com/chargeschare/backend/internal/api/middleware"
	"github.com/chargeschare/backend/internal/models"
	"github.com/chargeschare/backend/pkg/apperror"
)

// HostHandler handles host dashboard endpoints.
type HostHandler struct {
	db  *pgxpool.Pool
	log *zap.Logger
}

// NewHostHandler creates a new HostHandler.
func NewHostHandler(db *pgxpool.Pool, log *zap.Logger) *HostHandler {
	return &HostHandler{db: db, log: log}
}

// GetBookings handles GET /api/v1/host/bookings
func (h *HostHandler) GetBookings(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	limit := 20
	offset := 0
	if v := r.URL.Query().Get("limit"); v != "" {
		if i, err := strconv.Atoi(v); err == nil && i > 0 && i <= 100 {
			limit = i
		}
	}
	if v := r.URL.Query().Get("offset"); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			offset = i
		}
	}

	rows, err := h.db.Query(r.Context(),
		`SELECT b.id, b.wallbox_id, b.driver_id, b.start_time, b.end_time, b.status,
		        b.estimated_kwh, b.actual_kwh, b.price_per_kwh, b.total_amount,
		        b.stripe_payment_intent_id, b.stripe_charge_id, b.ocpp_transaction_id,
		        b.cancel_reason, b.created_at, b.updated_at
		   FROM bookings b
		   JOIN wallboxes w ON w.id = b.wallbox_id
		  WHERE w.owner_id = $1
		  ORDER BY b.created_at DESC
		  LIMIT $2 OFFSET $3`,
		userID, limit, offset,
	)
	if err != nil {
		h.log.Error("host list bookings", zap.Error(err))
		apperror.Internal("could not list bookings").Write(w)
		return
	}
	defer rows.Close()

	bookings := make([]*models.Booking, 0)
	for rows.Next() {
		var b models.Booking
		err := rows.Scan(
			&b.ID, &b.WallboxID, &b.DriverID, &b.StartTime, &b.EndTime, &b.Status,
			&b.EstimatedKwh, &b.ActualKwh, &b.PricePerKwh, &b.TotalAmount,
			&b.StripePaymentIntentID, &b.StripeChargeID, &b.OcppTransactionID,
			&b.CancelReason, &b.CreatedAt, &b.UpdatedAt,
		)
		if err != nil {
			h.log.Error("scan booking", zap.Error(err))
			continue
		}
		bookings = append(bookings, &b)
	}

	apperror.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"bookings": bookings,
		"limit":    limit,
		"offset":   offset,
	})
}

// GetEarnings handles GET /api/v1/host/earnings
func (h *HostHandler) GetEarnings(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	from := r.URL.Query().Get("from")
	to := r.URL.Query().Get("to")

	// Default: all time.
	fromClause := "1970-01-01"
	toClause := "9999-12-31"
	if from != "" {
		fromClause = from
	}
	if to != "" {
		toClause = to
	}

	var earnings models.HostEarnings
	err = h.db.QueryRow(r.Context(),
		`SELECT
		    COALESCE(SUM(b.total_amount), 0)                                                   AS total,
		    COALESCE(SUM(b.total_amount) FILTER (WHERE DATE_TRUNC('month', b.created_at) = DATE_TRUNC('month', NOW())), 0) AS month,
		    COUNT(b.id)                                                                         AS sessions,
		    COALESCE(AVG(r.rating), 0)                                                         AS avg_rating
		   FROM bookings b
		   JOIN wallboxes w ON w.id = b.wallbox_id
		   LEFT JOIN reviews r ON r.wallbox_id = w.id
		  WHERE w.owner_id = $1
		    AND b.status = 'completed'
		    AND b.created_at >= $2::date
		    AND b.created_at <= $3::date`,
		userID, fromClause, toClause,
	).Scan(&earnings.Total, &earnings.Month, &earnings.Sessions, &earnings.AvgRating)
	if err != nil {
		h.log.Error("host earnings query", zap.Error(err))
		apperror.Internal("could not get earnings").Write(w)
		return
	}

	apperror.WriteJSON(w, http.StatusOK, earnings)
}

// GetSessions handles GET /api/v1/host/sessions
func (h *HostHandler) GetSessions(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	limit := 20
	offset := 0
	if v := r.URL.Query().Get("limit"); v != "" {
		if i, err := strconv.Atoi(v); err == nil && i > 0 && i <= 100 {
			limit = i
		}
	}
	if v := r.URL.Query().Get("offset"); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			offset = i
		}
	}

	rows, err := h.db.Query(r.Context(),
		`SELECT cs.id, cs.booking_id, cs.ocpp_id, cs.transaction_id,
		        cs.meter_start, cs.meter_stop, cs.started_at, cs.stopped_at,
		        cs.stop_reason, cs.created_at,
		        u.id, u.name, u.email, u.avatar_url
		   FROM charging_sessions cs
		   JOIN bookings b ON b.id = cs.booking_id
		   JOIN users u ON u.id = b.driver_id
		   JOIN wallboxes w ON w.id = b.wallbox_id
		  WHERE w.owner_id = $1
		  ORDER BY cs.created_at DESC
		  LIMIT $2 OFFSET $3`,
		userID, limit, offset,
	)
	if err != nil {
		h.log.Error("host sessions query", zap.Error(err))
		apperror.Internal("could not get sessions").Write(w)
		return
	}
	defer rows.Close()

	sessions := make([]*models.ChargingSession, 0)
	for rows.Next() {
		var cs models.ChargingSession
		var driver models.User
		err := rows.Scan(
			&cs.ID, &cs.BookingID, &cs.OcppID, &cs.TransactionID,
			&cs.MeterStart, &cs.MeterStop, &cs.StartedAt, &cs.StoppedAt,
			&cs.StopReason, &cs.CreatedAt,
			&driver.ID, &driver.Name, &driver.Email, &driver.AvatarURL,
		)
		if err != nil {
			h.log.Error("scan session", zap.Error(err))
			continue
		}
		cs.Driver = &driver
		sessions = append(sessions, &cs)
	}

	apperror.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"sessions": sessions,
		"limit":    limit,
		"offset":   offset,
	})
}
