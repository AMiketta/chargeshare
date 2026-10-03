package middleware

import (
	"net/http"
	"time"

	"github.com/go-chi/httprate"
)

// RateLimit returns a rate limiting middleware.
// limit: max requests per window
// windowLength: time window duration
func RateLimit(limit int, windowLength time.Duration) func(http.Handler) http.Handler {
	return httprate.LimitByIP(limit, windowLength)
}

// AuthRateLimit returns a stricter rate limiter for auth endpoints.
func AuthRateLimit() func(http.Handler) http.Handler {
	return httprate.LimitByIP(10, 1*time.Minute)
}

// APIRateLimit returns the standard API rate limiter.
func APIRateLimit() func(http.Handler) http.Handler {
	return httprate.LimitByIP(120, 1*time.Minute)
}
