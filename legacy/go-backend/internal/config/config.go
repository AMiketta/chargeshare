package config

import (
	"fmt"
	"os"
	"strconv"

	"github.com/joho/godotenv"
)

// Config holds all application configuration loaded from environment variables.
type Config struct {
	// Server
	Port string
	Env  string

	// Database
	DatabaseURL string

	// JWT
	JWTSecret        string
	JWTRefreshSecret string

	// Stripe
	StripeSecretKey       string
	StripeWebhookSecret   string
	StripePlatformFeePercent int64

	// Apple
	AppleBundleID string

	// OCPP
	OCPPPath string

	// Logging
	LogLevel string
}

// Load reads configuration from environment variables (and optionally .env file).
func Load() (*Config, error) {
	// Load .env file if it exists; ignore error if it doesn't.
	_ = godotenv.Load()

	cfg := &Config{
		Port:             getEnvOrDefault("PORT", "8080"),
		Env:              getEnvOrDefault("ENV", "development"),
		DatabaseURL:      os.Getenv("DATABASE_URL"),
		JWTSecret:        os.Getenv("JWT_SECRET"),
		JWTRefreshSecret: os.Getenv("JWT_REFRESH_SECRET"),
		StripeSecretKey:  os.Getenv("STRIPE_SECRET_KEY"),
		StripeWebhookSecret: os.Getenv("STRIPE_WEBHOOK_SECRET"),
		AppleBundleID:    getEnvOrDefault("APPLE_BUNDLE_ID", "de.chargeschare.app"),
		OCPPPath:         getEnvOrDefault("OCPP_PATH", "/ocpp"),
		LogLevel:         getEnvOrDefault("LOG_LEVEL", "info"),
	}

	feeStr := getEnvOrDefault("STRIPE_PLATFORM_FEE_PERCENT", "15")
	fee, err := strconv.ParseInt(feeStr, 10, 64)
	if err != nil {
		return nil, fmt.Errorf("invalid STRIPE_PLATFORM_FEE_PERCENT: %w", err)
	}
	cfg.StripePlatformFeePercent = fee

	if err := cfg.validate(); err != nil {
		return nil, err
	}

	return cfg, nil
}

func (c *Config) validate() error {
	if c.DatabaseURL == "" {
		return fmt.Errorf("DATABASE_URL is required")
	}
	if c.JWTSecret == "" {
		return fmt.Errorf("JWT_SECRET is required")
	}
	if c.JWTRefreshSecret == "" {
		return fmt.Errorf("JWT_REFRESH_SECRET is required")
	}
	return nil
}

func getEnvOrDefault(key, defaultVal string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return defaultVal
}

// IsDevelopment returns true when running in development mode.
func (c *Config) IsDevelopment() bool {
	return c.Env == "development"
}
