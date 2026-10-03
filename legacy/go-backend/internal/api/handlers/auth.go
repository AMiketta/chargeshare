package handlers

import (
	"context"
	"encoding/json"
	"net/http"

	"github.com/go-playground/validator/v10"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"

	mw "github.com/chargeschare/backend/internal/api/middleware"
	"github.com/chargeschare/backend/internal/models"
	"github.com/chargeschare/backend/internal/services"
	"github.com/chargeschare/backend/pkg/apperror"
)

// AuthHandler handles authentication endpoints.
type AuthHandler struct {
	db         *pgxpool.Pool
	authSvc    *services.AuthService
	paymentSvc *services.PaymentService
	validate   *validator.Validate
	log        *zap.Logger
}

// NewAuthHandler creates a new AuthHandler.
func NewAuthHandler(
	db *pgxpool.Pool,
	authSvc *services.AuthService,
	paymentSvc *services.PaymentService,
	validate *validator.Validate,
	log *zap.Logger,
) *AuthHandler {
	return &AuthHandler{
		db:         db,
		authSvc:    authSvc,
		paymentSvc: paymentSvc,
		validate:   validate,
		log:        log,
	}
}

// Register handles POST /api/v1/auth/register
func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	var req models.RegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		apperror.ValidationError(err.Error()).Write(w)
		return
	}

	ctx := r.Context()

	// Check email uniqueness.
	var count int
	h.db.QueryRow(ctx, `SELECT COUNT(*) FROM users WHERE email = $1`, req.Email).Scan(&count)
	if count > 0 {
		apperror.Conflict("email already registered").Write(w)
		return
	}

	hash, err := services.HashPassword(req.Password)
	if err != nil {
		apperror.Internal("could not hash password").Write(w)
		return
	}

	var user models.User
	err = h.db.QueryRow(ctx,
		`INSERT INTO users (email, password_hash, name) VALUES ($1, $2, $3)
		 RETURNING id, email, password_hash, apple_sub, name, phone, role,
		           stripe_customer_id, stripe_account_id, avatar_url, is_verified,
		           created_at, updated_at`,
		req.Email, hash, req.Name,
	).Scan(
		&user.ID, &user.Email, &user.PasswordHash, &user.AppleSub, &user.Name,
		&user.Phone, &user.Role, &user.StripeCustomerID, &user.StripeAccountID,
		&user.AvatarURL, &user.IsVerified, &user.CreatedAt, &user.UpdatedAt,
	)
	if err != nil {
		h.log.Error("insert user", zap.Error(err))
		apperror.Internal("could not create user").Write(w)
		return
	}

	// Create Stripe customer in background (best-effort).
	go func() {
		cust, err := h.paymentSvc.CreateStripeCustomer(user.Email, user.Name)
		if err != nil {
			h.log.Warn("stripe customer creation failed", zap.Error(err))
			return
		}
		h.db.Exec(context.Background(),
			`UPDATE users SET stripe_customer_id = $1 WHERE id = $2`,
			cust.ID, user.ID,
		)
	}()

	accessToken, err := h.authSvc.GenerateAccessToken(user.ID.String(), user.Email, string(user.Role))
	if err != nil {
		apperror.Internal("token generation failed").Write(w)
		return
	}
	refreshToken, err := h.authSvc.GenerateRefreshToken(ctx, user.ID)
	if err != nil {
		apperror.Internal("token generation failed").Write(w)
		return
	}

	apperror.WriteJSON(w, http.StatusCreated, models.AuthResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         &user,
	})
}

// Login handles POST /api/v1/auth/login
func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req models.LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		apperror.ValidationError(err.Error()).Write(w)
		return
	}

	ctx := r.Context()
	var user models.User
	err := h.db.QueryRow(ctx,
		`SELECT id, email, password_hash, apple_sub, name, phone, role,
		        stripe_customer_id, stripe_account_id, avatar_url, is_verified,
		        created_at, updated_at
		   FROM users WHERE email = $1`,
		req.Email,
	).Scan(
		&user.ID, &user.Email, &user.PasswordHash, &user.AppleSub, &user.Name,
		&user.Phone, &user.Role, &user.StripeCustomerID, &user.StripeAccountID,
		&user.AvatarURL, &user.IsVerified, &user.CreatedAt, &user.UpdatedAt,
	)
	if err != nil {
		apperror.Unauthorized("invalid credentials").Write(w)
		return
	}
	if user.PasswordHash == nil || !services.CheckPassword(req.Password, *user.PasswordHash) {
		apperror.Unauthorized("invalid credentials").Write(w)
		return
	}

	accessToken, err := h.authSvc.GenerateAccessToken(user.ID.String(), user.Email, string(user.Role))
	if err != nil {
		apperror.Internal("token generation failed").Write(w)
		return
	}
	refreshToken, err := h.authSvc.GenerateRefreshToken(ctx, user.ID)
	if err != nil {
		apperror.Internal("token generation failed").Write(w)
		return
	}

	apperror.WriteJSON(w, http.StatusOK, models.AuthResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         &user,
	})
}

// AppleAuth handles POST /api/v1/auth/apple
func (h *AuthHandler) AppleAuth(w http.ResponseWriter, r *http.Request) {
	var req models.AppleAuthRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		apperror.ValidationError(err.Error()).Write(w)
		return
	}

	ctx := r.Context()
	sub, email, err := h.authSvc.VerifyAppleToken(ctx, req.IdentityToken)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}

	name := coalesce(req.FullName, email)

	// Upsert user.
	var user models.User
	err = h.db.QueryRow(ctx,
		`INSERT INTO users (email, apple_sub, name)
		 VALUES ($1, $2, $3)
		 ON CONFLICT (apple_sub) DO UPDATE SET updated_at = NOW()
		 RETURNING id, email, password_hash, apple_sub, name, phone, role,
		           stripe_customer_id, stripe_account_id, avatar_url, is_verified,
		           created_at, updated_at`,
		email, sub, name,
	).Scan(
		&user.ID, &user.Email, &user.PasswordHash, &user.AppleSub, &user.Name,
		&user.Phone, &user.Role, &user.StripeCustomerID, &user.StripeAccountID,
		&user.AvatarURL, &user.IsVerified, &user.CreatedAt, &user.UpdatedAt,
	)
	if err != nil {
		h.log.Error("apple auth upsert user", zap.Error(err))
		apperror.Internal("could not create user").Write(w)
		return
	}

	accessToken, _ := h.authSvc.GenerateAccessToken(user.ID.String(), user.Email, string(user.Role))
	refreshToken, _ := h.authSvc.GenerateRefreshToken(ctx, user.ID)

	apperror.WriteJSON(w, http.StatusOK, models.AuthResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         &user,
	})
}

// Refresh handles POST /api/v1/auth/refresh
func (h *AuthHandler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req models.RefreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}

	ctx := r.Context()
	user, err := h.authSvc.ValidateRefreshToken(ctx, req.RefreshToken)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}

	// Rotate: revoke old, issue new.
	_ = h.authSvc.RevokeRefreshToken(ctx, req.RefreshToken)

	accessToken, _ := h.authSvc.GenerateAccessToken(user.ID.String(), user.Email, string(user.Role))
	newRefresh, err := h.authSvc.GenerateRefreshToken(ctx, user.ID)
	if err != nil {
		apperror.Internal("token generation failed").Write(w)
		return
	}

	apperror.WriteJSON(w, http.StatusOK, map[string]string{
		"access_token":  accessToken,
		"refresh_token": newRefresh,
	})
}

// Logout handles POST /api/v1/auth/logout
func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	var req models.LogoutRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}

	userIDStr := mw.GetUserID(r.Context())
	h.log.Info("user logout", zap.String("user_id", userIDStr))

	_ = h.authSvc.RevokeRefreshToken(r.Context(), req.RefreshToken)
	w.WriteHeader(http.StatusNoContent)
}

// coalesce returns the first non-nil string, or the fallback.
func coalesce(s *string, fallback string) string {
	if s != nil && *s != "" {
		return *s
	}
	return fallback
}
