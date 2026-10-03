package services

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"
	"golang.org/x/crypto/bcrypt"

	"github.com/chargeschare/backend/internal/models"
	"github.com/chargeschare/backend/pkg/apperror"
)

const (
	accessTokenDuration  = 15 * time.Minute
	refreshTokenDuration = 30 * 24 * time.Hour
	bcryptCost           = 12
)

// JWTClaims contains the claims embedded in access tokens.
type JWTClaims struct {
	UserID string `json:"user_id"`
	Email  string `json:"email"`
	Role   string `json:"role"`
	jwt.RegisteredClaims
}

// AuthService handles authentication business logic.
type AuthService struct {
	db               *pgxpool.Pool
	jwtSecret        []byte
	jwtRefreshSecret []byte
	appleBundleID    string
	log              *zap.Logger
}

// NewAuthService creates a new AuthService.
func NewAuthService(db *pgxpool.Pool, jwtSecret, jwtRefreshSecret, appleBundleID string, log *zap.Logger) *AuthService {
	return &AuthService{
		db:               db,
		jwtSecret:        []byte(jwtSecret),
		jwtRefreshSecret: []byte(jwtRefreshSecret),
		appleBundleID:    appleBundleID,
		log:              log,
	}
}

// DB returns the database pool (for use by handlers).
func (s *AuthService) DB() *pgxpool.Pool {
	return s.db
}

// HashPassword hashes a plaintext password.
func HashPassword(password string) (string, error) {
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcryptCost)
	if err != nil {
		return "", err
	}
	return string(hash), nil
}

// CheckPassword verifies a password against a hash.
func CheckPassword(password, hash string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(password)) == nil
}

// GenerateAccessToken creates a signed JWT access token.
func (s *AuthService) GenerateAccessToken(userID, email, role string) (string, error) {
	now := time.Now().UTC()
	claims := JWTClaims{
		UserID: userID,
		Email:  email,
		Role:   role,
		RegisteredClaims: jwt.RegisteredClaims{
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(accessTokenDuration)),
			Issuer:    "chargeschare",
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(s.jwtSecret)
}

// ValidateAccessToken parses and validates a JWT access token.
func (s *AuthService) ValidateAccessToken(tokenStr string) (*JWTClaims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &JWTClaims{}, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return s.jwtSecret, nil
	})
	if err != nil {
		return nil, apperror.Unauthorized("invalid or expired token")
	}
	claims, ok := token.Claims.(*JWTClaims)
	if !ok || !token.Valid {
		return nil, apperror.Unauthorized("invalid token claims")
	}
	return claims, nil
}

// GenerateRefreshToken creates a refresh token, hashes it, and stores it in DB.
func (s *AuthService) GenerateRefreshToken(ctx context.Context, userID uuid.UUID) (string, error) {
	// Generate a cryptographically random token.
	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return "", fmt.Errorf("generate token bytes: %w", err)
	}
	tokenStr := base64.URLEncoding.EncodeToString(raw)
	tokenHash := hashToken(tokenStr)

	expiresAt := time.Now().UTC().Add(refreshTokenDuration)

	_, err := s.db.Exec(ctx,
		`INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES ($1, $2, $3)`,
		userID, tokenHash, expiresAt,
	)
	if err != nil {
		return "", fmt.Errorf("store refresh token: %w", err)
	}
	return tokenStr, nil
}

// ValidateRefreshToken looks up a refresh token hash in the DB and returns the associated user.
func (s *AuthService) ValidateRefreshToken(ctx context.Context, tokenStr string) (*models.User, error) {
	tokenHash := hashToken(tokenStr)

	var rt models.RefreshToken
	err := s.db.QueryRow(ctx,
		`SELECT id, user_id, token_hash, expires_at FROM refresh_tokens WHERE token_hash = $1`,
		tokenHash,
	).Scan(&rt.ID, &rt.UserID, &rt.TokenHash, &rt.ExpiresAt)
	if err != nil {
		return nil, apperror.Unauthorized("invalid refresh token")
	}
	if time.Now().UTC().After(rt.ExpiresAt) {
		return nil, apperror.Unauthorized("refresh token expired")
	}

	user, err := getUserByID(ctx, s.db, rt.UserID)
	if err != nil {
		return nil, err
	}
	return user, nil
}

// RevokeRefreshToken deletes a refresh token from the DB.
func (s *AuthService) RevokeRefreshToken(ctx context.Context, tokenStr string) error {
	tokenHash := hashToken(tokenStr)
	_, err := s.db.Exec(ctx, `DELETE FROM refresh_tokens WHERE token_hash = $1`, tokenHash)
	return err
}

// applePublicKey holds a single key from Apple's JWKS endpoint.
type applePublicKey struct {
	Kty string `json:"kty"`
	Kid string `json:"kid"`
	Use string `json:"use"`
	Alg string `json:"alg"`
	N   string `json:"n"`
	E   string `json:"e"`
}

type appleKeysResponse struct {
	Keys []applePublicKey `json:"keys"`
}

// VerifyAppleToken verifies an Apple identity token and returns sub and email.
// For the MVP it fetches Apple's public keys and validates the JWT signature.
func (s *AuthService) VerifyAppleToken(ctx context.Context, identityToken string) (sub, email string, err error) {
	// Fetch Apple's public keys.
	resp, err := http.Get("https://appleid.apple.com/auth/keys")
	if err != nil {
		return "", "", fmt.Errorf("fetch apple keys: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", "", fmt.Errorf("read apple keys: %w", err)
	}

	var keysResp appleKeysResponse
	if err := json.Unmarshal(body, &keysResp); err != nil {
		return "", "", fmt.Errorf("parse apple keys: %w", err)
	}

	// Parse the token header to find the key ID.
	unverified, _, err := jwt.NewParser().ParseUnverified(identityToken, jwt.MapClaims{})
	if err != nil {
		return "", "", apperror.Unauthorized("invalid apple token format")
	}
	kid, _ := unverified.Header["kid"].(string)

	// Find the matching key and build an RSA public key.
	var matchedKey *applePublicKey
	for i, k := range keysResp.Keys {
		if k.Kid == kid {
			matchedKey = &keysResp.Keys[i]
			break
		}
	}
	if matchedKey == nil {
		return "", "", apperror.Unauthorized("apple key not found")
	}

	pubKey, err := buildRSAPublicKey(matchedKey)
	if err != nil {
		return "", "", fmt.Errorf("build rsa key: %w", err)
	}

	token, err := jwt.Parse(identityToken, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return pubKey, nil
	})
	if err != nil || !token.Valid {
		return "", "", apperror.Unauthorized("invalid apple token signature")
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return "", "", apperror.Unauthorized("invalid apple token claims")
	}

	sub, _ = claims["sub"].(string)
	email, _ = claims["email"].(string)
	if sub == "" {
		return "", "", apperror.Unauthorized("missing sub in apple token")
	}
	return sub, email, nil
}

// buildRSAPublicKey reconstructs an *rsa.PublicKey from Apple's JWK fields.
func buildRSAPublicKey(key *applePublicKey) (interface{}, error) {
	nBytes, err := base64.RawURLEncoding.DecodeString(key.N)
	if err != nil {
		return nil, err
	}
	eBytes, err := base64.RawURLEncoding.DecodeString(key.E)
	if err != nil {
		return nil, err
	}

	n := new(big.Int).SetBytes(nBytes)
	eBig := new(big.Int).SetBytes(eBytes)

	return buildRSAPubKey(n, int(eBig.Int64())), nil
}

// getUserByID fetches a user by UUID from the DB.
func getUserByID(ctx context.Context, db *pgxpool.Pool, id uuid.UUID) (*models.User, error) {
	var u models.User
	err := db.QueryRow(ctx,
		`SELECT id, email, password_hash, apple_sub, name, phone, role,
		        stripe_customer_id, stripe_account_id, avatar_url, is_verified,
		        created_at, updated_at
		   FROM users WHERE id = $1`,
		id,
	).Scan(
		&u.ID, &u.Email, &u.PasswordHash, &u.AppleSub, &u.Name, &u.Phone, &u.Role,
		&u.StripeCustomerID, &u.StripeAccountID, &u.AvatarURL, &u.IsVerified,
		&u.CreatedAt, &u.UpdatedAt,
	)
	if err != nil {
		return nil, apperror.NotFound("user not found")
	}
	return &u, nil
}

// hashToken returns the SHA-256 hex digest of the token string.
func hashToken(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}

// GenerateNumericID returns a random 8-digit integer ID (for OCPP transaction IDs).
func GenerateNumericID() (int, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(90_000_000))
	if err != nil {
		return 0, err
	}
	return int(n.Int64()) + 10_000_000, nil
}
