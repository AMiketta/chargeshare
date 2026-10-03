package middleware

import (
	"context"
	"net/http"
	"strings"

	"github.com/chargeschare/backend/internal/services"
	"github.com/chargeschare/backend/pkg/apperror"
)

// contextKey is the type for context keys used in this package.
type contextKey string

const (
	// ContextKeyUserID is the context key for the authenticated user ID.
	ContextKeyUserID contextKey = "user_id"
	// ContextKeyUserEmail is the context key for the authenticated user email.
	ContextKeyUserEmail contextKey = "user_email"
	// ContextKeyUserRole is the context key for the authenticated user role.
	ContextKeyUserRole contextKey = "user_role"
)

// Authenticate is a middleware that validates JWT access tokens.
func Authenticate(authSvc *services.AuthService) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				apperror.Unauthorized("missing Authorization header").Write(w)
				return
			}

			parts := strings.SplitN(authHeader, " ", 2)
			if len(parts) != 2 || strings.ToLower(parts[0]) != "bearer" {
				apperror.Unauthorized("invalid Authorization header format").Write(w)
				return
			}

			claims, err := authSvc.ValidateAccessToken(parts[1])
			if err != nil {
				apperror.WriteError(w, err)
				return
			}

			ctx := context.WithValue(r.Context(), ContextKeyUserID, claims.UserID)
			ctx = context.WithValue(ctx, ContextKeyUserEmail, claims.Email)
			ctx = context.WithValue(ctx, ContextKeyUserRole, claims.Role)

			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// RequireRole is a middleware that enforces a minimum role.
// Allowed roles: "driver", "host", "both", "admin"
func RequireRole(roles ...string) func(http.Handler) http.Handler {
	allowed := make(map[string]bool, len(roles))
	for _, r := range roles {
		allowed[r] = true
	}
	// "admin" always has access.
	allowed["admin"] = true

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			role, _ := r.Context().Value(ContextKeyUserRole).(string)
			if !allowed[role] {
				apperror.Forbidden("insufficient role").Write(w)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// GetUserID extracts the authenticated user ID from context.
func GetUserID(ctx context.Context) string {
	id, _ := ctx.Value(ContextKeyUserID).(string)
	return id
}

// GetUserRole extracts the authenticated user role from context.
func GetUserRole(ctx context.Context) string {
	role, _ := ctx.Value(ContextKeyUserRole).(string)
	return role
}
