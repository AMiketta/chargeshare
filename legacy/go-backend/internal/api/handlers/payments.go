package handlers

import (
	"io"
	"net/http"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"

	mw "github.com/chargeschare/backend/internal/api/middleware"
	"github.com/chargeschare/backend/internal/services"
	"github.com/chargeschare/backend/pkg/apperror"
)

// PaymentsHandler handles payment endpoints.
type PaymentsHandler struct {
	db         *pgxpool.Pool
	paymentSvc *services.PaymentService
	log        *zap.Logger
}

// NewPaymentsHandler creates a new PaymentsHandler.
func NewPaymentsHandler(db *pgxpool.Pool, paymentSvc *services.PaymentService, log *zap.Logger) *PaymentsHandler {
	return &PaymentsHandler{db: db, paymentSvc: paymentSvc, log: log}
}

// Onboard handles POST /api/v1/payments/onboard
func (h *PaymentsHandler) Onboard(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	ctx := r.Context()

	var email string
	var stripeAccountID *string
	err = h.db.QueryRow(ctx,
		`SELECT email, stripe_account_id FROM users WHERE id = $1`, userID,
	).Scan(&email, &stripeAccountID)
	if err != nil {
		apperror.NotFound("user not found").Write(w)
		return
	}

	url, err := h.paymentSvc.CreateConnectOnboardingLink(ctx, userID.String(), email, stripeAccountID)
	if err != nil {
		h.log.Error("create onboarding link", zap.Error(err))
		apperror.Internal("could not create onboarding link").Write(w)
		return
	}

	apperror.WriteJSON(w, http.StatusOK, map[string]string{"url": url})
}

// OnboardReturn handles GET /api/v1/payments/onboard/return
func (h *PaymentsHandler) OnboardReturn(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	// Update user role to include host.
	_, err = h.db.Exec(r.Context(),
		`UPDATE users
		    SET role = CASE WHEN role = 'driver' THEN 'both' ELSE role END,
		        updated_at = NOW()
		  WHERE id = $1`,
		userID,
	)
	if err != nil {
		h.log.Error("update user role after onboarding", zap.Error(err))
	}

	apperror.WriteJSON(w, http.StatusOK, map[string]string{"message": "onboarding complete"})
}

// Webhook handles POST /api/v1/payments/webhook
func (h *PaymentsHandler) Webhook(w http.ResponseWriter, r *http.Request) {
	const maxBodyBytes = 65536
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	payload, err := io.ReadAll(r.Body)
	if err != nil {
		apperror.BadRequest("could not read request body").Write(w)
		return
	}

	sigHeader := r.Header.Get("Stripe-Signature")
	if err := h.paymentSvc.HandleWebhook(payload, sigHeader); err != nil {
		h.log.Error("webhook handling failed", zap.Error(err))
		apperror.BadRequest(err.Error()).Write(w)
		return
	}

	w.WriteHeader(http.StatusOK)
}
