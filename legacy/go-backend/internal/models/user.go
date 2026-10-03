package models

import (
	"time"

	"github.com/google/uuid"
)

// UserRole represents a user's role in the system.
type UserRole string

const (
	RoleDriver UserRole = "driver"
	RoleHost   UserRole = "host"
	RoleBoth   UserRole = "both"
	RoleAdmin  UserRole = "admin"
)

// User represents a registered user.
type User struct {
	ID               uuid.UUID `json:"id"                db:"id"`
	Email            string    `json:"email"             db:"email"`
	PasswordHash     *string   `json:"-"                 db:"password_hash"`
	AppleSub         *string   `json:"-"                 db:"apple_sub"`
	Name             string    `json:"name"              db:"name"`
	Phone            *string   `json:"phone,omitempty"   db:"phone"`
	Role             UserRole  `json:"role"              db:"role"`
	StripeCustomerID *string   `json:"stripe_customer_id,omitempty" db:"stripe_customer_id"`
	StripeAccountID  *string   `json:"stripe_account_id,omitempty"  db:"stripe_account_id"`
	AvatarURL        *string   `json:"avatar_url,omitempty"         db:"avatar_url"`
	IsVerified       bool      `json:"is_verified"       db:"is_verified"`
	CreatedAt        time.Time `json:"created_at"        db:"created_at"`
	UpdatedAt        time.Time `json:"updated_at"        db:"updated_at"`
}

// RegisterRequest is the payload for user registration.
type RegisterRequest struct {
	Email    string `json:"email"    validate:"required,email"`
	Password string `json:"password" validate:"required,min=8"`
	Name     string `json:"name"     validate:"required,min=1,max=100"`
}

// LoginRequest is the payload for email/password login.
type LoginRequest struct {
	Email    string `json:"email"    validate:"required,email"`
	Password string `json:"password" validate:"required"`
}

// AppleAuthRequest is the payload for Sign in with Apple.
type AppleAuthRequest struct {
	IdentityToken string  `json:"identity_token" validate:"required"`
	FullName      *string `json:"full_name,omitempty"`
}

// RefreshRequest is the payload for token refresh.
type RefreshRequest struct {
	RefreshToken string `json:"refresh_token" validate:"required"`
}

// LogoutRequest is the payload for logout.
type LogoutRequest struct {
	RefreshToken string `json:"refresh_token" validate:"required"`
}

// UpdateUserRequest is the payload for updating user profile.
type UpdateUserRequest struct {
	Name      *string `json:"name,omitempty"       validate:"omitempty,min=1,max=100"`
	Phone     *string `json:"phone,omitempty"      validate:"omitempty"`
	AvatarURL *string `json:"avatar_url,omitempty" validate:"omitempty,url"`
}

// UpdateRoleRequest is the payload for changing user role.
type UpdateRoleRequest struct {
	Role UserRole `json:"role" validate:"required,oneof=driver host both"`
}

// AuthResponse is the response returned after login/register/refresh.
type AuthResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	User         *User  `json:"user"`
}
