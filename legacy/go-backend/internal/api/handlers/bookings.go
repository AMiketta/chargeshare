package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"

	mw "github.com/chargeschare/backend/internal/api/middleware"
	"github.com/chargeschare/backend/internal/models"
	"github.com/chargeschare/backend/internal/services"
	"github.com/chargeschare/backend/pkg/apperror"
)

// BookingsHandler handles booking endpoints.
type BookingsHandler struct {
	db         *pgxpool.Pool
	bookingSvc *services.BookingService
	validate   *validator.Validate
	log        *zap.Logger
}

// NewBookingsHandler creates a new BookingsHandler.
func NewBookingsHandler(db *pgxpool.Pool, bookingSvc *services.BookingService, validate *validator.Validate, log *zap.Logger) *BookingsHandler {
	return &BookingsHandler{db: db, bookingSvc: bookingSvc, validate: validate, log: log}
}

// List handles GET /api/v1/bookings
func (h *BookingsHandler) List(w http.ResponseWriter, r *http.Request) {
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
		`SELECT id, wallbox_id, driver_id, start_time, end_time, status,
		        estimated_kwh, actual_kwh, price_per_kwh, total_amount,
		        stripe_payment_intent_id, stripe_charge_id, ocpp_transaction_id,
		        cancel_reason, created_at, updated_at
		   FROM bookings
		  WHERE driver_id = $1
		  ORDER BY created_at DESC
		  LIMIT $2 OFFSET $3`,
		userID, limit, offset,
	)
	if err != nil {
		h.log.Error("list bookings", zap.Error(err))
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

// Create handles POST /api/v1/bookings
func (h *BookingsHandler) Create(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	var req models.CreateBookingRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		apperror.ValidationError(err.Error()).Write(w)
		return
	}

	wallboxID, err := uuid.Parse(req.WallboxID)
	if err != nil {
		apperror.BadRequest("invalid wallbox_id").Write(w)
		return
	}

	startTime, err := time.Parse(time.RFC3339, req.StartTime)
	if err != nil {
		apperror.BadRequest("invalid start_time, use RFC3339").Write(w)
		return
	}
	endTime, err := time.Parse(time.RFC3339, req.EndTime)
	if err != nil {
		apperror.BadRequest("invalid end_time, use RFC3339").Write(w)
		return
	}

	if !endTime.After(startTime) {
		apperror.BadRequest("end_time must be after start_time").Write(w)
		return
	}
	if startTime.Before(time.Now().UTC()) {
		apperror.BadRequest("start_time must be in the future").Write(w)
		return
	}

	// Get stripe customer ID.
	var stripeCustomerID string
	h.db.QueryRow(r.Context(),
		`SELECT COALESCE(stripe_customer_id, '') FROM users WHERE id = $1`, userID,
	).Scan(&stripeCustomerID)

	booking, pi, err := h.bookingSvc.CreateBookingWithPayment(
		r.Context(), wallboxID, userID, startTime, endTime, stripeCustomerID,
	)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}

	clientSecret := pi.ClientSecret
	apperror.WriteJSON(w, http.StatusCreated, models.BookingResponse{
		Booking:      booking,
		ClientSecret: &clientSecret,
	})
}

// GetByID handles GET /api/v1/bookings/:id
func (h *BookingsHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	bookingID, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid booking id").Write(w)
		return
	}

	booking, err := h.bookingSvc.GetBookingByID(r.Context(), bookingID)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}

	// Allow driver, wallbox owner (host), or admin.
	role := mw.GetUserRole(r.Context())
	if booking.DriverID != userID && role != "admin" {
		// Check if user is wallbox owner.
		var ownerID uuid.UUID
		h.db.QueryRow(r.Context(),
			`SELECT owner_id FROM wallboxes WHERE id = $1`, booking.WallboxID,
		).Scan(&ownerID)
		if ownerID != userID {
			apperror.Forbidden("access denied").Write(w)
			return
		}
	}

	apperror.WriteJSON(w, http.StatusOK, booking)
}

// Cancel handles DELETE /api/v1/bookings/:id
func (h *BookingsHandler) Cancel(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	bookingID, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid booking id").Write(w)
		return
	}

	var req models.CancelBookingRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		// Body is optional for cancel.
		req = models.CancelBookingRequest{}
	}

	if err := h.bookingSvc.CancelBooking(r.Context(), bookingID, userID, req.Reason); err != nil {
		apperror.WriteError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ConfirmPayment handles POST /api/v1/bookings/:id/confirm-payment
func (h *BookingsHandler) ConfirmPayment(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	bookingID, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		apperror.BadRequest("invalid booking id").Write(w)
		return
	}

	booking, err := h.bookingSvc.ConfirmPayment(r.Context(), bookingID, userID)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, booking)
}
