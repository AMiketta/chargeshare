package handlers

import (
	"context"
	"encoding/json"
	"net/http"

	"github.com/go-playground/validator/v10"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"

	mw "github.com/chargeschare/backend/internal/api/middleware"
	"github.com/chargeschare/backend/internal/models"
	"github.com/chargeschare/backend/pkg/apperror"
)

// UsersHandler handles user profile endpoints.
type UsersHandler struct {
	db       *pgxpool.Pool
	validate *validator.Validate
	log      *zap.Logger
}

// NewUsersHandler creates a new UsersHandler.
func NewUsersHandler(db *pgxpool.Pool, validate *validator.Validate, log *zap.Logger) *UsersHandler {
	return &UsersHandler{db: db, validate: validate, log: log}
}

// GetMe handles GET /api/v1/users/me
func (h *UsersHandler) GetMe(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}
	user, err := scanUser(r.Context(), h.db, userID)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, user)
}

// UpdateMe handles PUT /api/v1/users/me
func (h *UsersHandler) UpdateMe(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	var req models.UpdateUserRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		apperror.ValidationError(err.Error()).Write(w)
		return
	}

	ctx := r.Context()
	_, err = h.db.Exec(ctx,
		`UPDATE users
		    SET name       = COALESCE($2, name),
		        phone      = COALESCE($3, phone),
		        avatar_url = COALESCE($4, avatar_url),
		        updated_at = NOW()
		  WHERE id = $1`,
		userID, req.Name, req.Phone, req.AvatarURL,
	)
	if err != nil {
		h.log.Error("update user", zap.Error(err))
		apperror.Internal("could not update user").Write(w)
		return
	}

	user, err := scanUser(ctx, h.db, userID)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, user)
}

// DeleteMe handles DELETE /api/v1/users/me
func (h *UsersHandler) DeleteMe(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}
	_, err = h.db.Exec(r.Context(), `DELETE FROM users WHERE id = $1`, userID)
	if err != nil {
		h.log.Error("delete user", zap.Error(err))
		apperror.Internal("could not delete user").Write(w)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// UpdateRole handles PUT /api/v1/users/me/role
func (h *UsersHandler) UpdateRole(w http.ResponseWriter, r *http.Request) {
	userID, err := uuid.Parse(mw.GetUserID(r.Context()))
	if err != nil {
		apperror.Unauthorized("invalid user id").Write(w)
		return
	}

	var req models.UpdateRoleRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.BadRequest("invalid JSON body").Write(w)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		apperror.ValidationError(err.Error()).Write(w)
		return
	}

	ctx := r.Context()
	_, err = h.db.Exec(ctx,
		`UPDATE users SET role = $2, updated_at = NOW() WHERE id = $1`,
		userID, string(req.Role),
	)
	if err != nil {
		apperror.Internal("could not update role").Write(w)
		return
	}

	user, err := scanUser(ctx, h.db, userID)
	if err != nil {
		apperror.WriteError(w, err)
		return
	}
	apperror.WriteJSON(w, http.StatusOK, user)
}

// scanUser fetches a user by ID.
func scanUser(ctx context.Context, db *pgxpool.Pool, id uuid.UUID) (*models.User, error) {
	var u models.User
	err := db.QueryRow(ctx,
		`SELECT id, email, password_hash, apple_sub, name, phone, role,
		        stripe_customer_id, stripe_account_id, avatar_url, is_verified,
		        created_at, updated_at
		   FROM users WHERE id = $1`,
		id,
	).Scan(
		&u.ID, &u.Email, &u.PasswordHash, &u.AppleSub, &u.Name,
		&u.Phone, &u.Role, &u.StripeCustomerID, &u.StripeAccountID,
		&u.AvatarURL, &u.IsVerified, &u.CreatedAt, &u.UpdatedAt,
	)
	if err != nil {
		return nil, apperror.NotFound("user not found")
	}
	return &u, nil
}
