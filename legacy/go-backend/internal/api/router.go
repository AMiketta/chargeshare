package api

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	chimw "github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"github.com/go-playground/validator/v10"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"

	"github.com/chargeschare/backend/internal/api/handlers"
	mw "github.com/chargeschare/backend/internal/api/middleware"
	"github.com/chargeschare/backend/internal/ocpp"
	"github.com/chargeschare/backend/internal/services"
)

// RouterDeps holds all dependencies for the HTTP router.
type RouterDeps struct {
	DB         *pgxpool.Pool
	AuthSvc    *services.AuthService
	BookingSvc *services.BookingService
	PaymentSvc *services.PaymentService
	WallboxSvc *services.WallboxService
	OCPPServer *ocpp.OCPPServer
	Log        *zap.Logger
	Validate   *validator.Validate
}

// NewRouter builds and returns the configured chi router.
func NewRouter(deps RouterDeps) http.Handler {
	r := chi.NewRouter()

	// Global middleware.
	r.Use(chimw.RequestID)
	r.Use(chimw.RealIP)
	r.Use(chimw.Recoverer)
	r.Use(mw.RequestLogger(deps.Log))
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"https://*", "http://*"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-Request-ID"},
		ExposedHeaders:   []string{"Link"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	// Handlers.
	authH := handlers.NewAuthHandler(deps.DB, deps.AuthSvc, deps.PaymentSvc, deps.Validate, deps.Log)
	usersH := handlers.NewUsersHandler(deps.DB, deps.Validate, deps.Log)
	wallboxH := handlers.NewWallboxHandler(deps.DB, deps.WallboxSvc, deps.Validate, deps.Log)
	bookingsH := handlers.NewBookingsHandler(deps.DB, deps.BookingSvc, deps.Validate, deps.Log)
	reviewsH := handlers.NewReviewsHandler(deps.DB, deps.Validate, deps.Log)
	paymentsH := handlers.NewPaymentsHandler(deps.DB, deps.PaymentSvc, deps.Log)
	hostH := handlers.NewHostHandler(deps.DB, deps.Log)

	authMiddleware := mw.Authenticate(deps.AuthSvc)

	// OCPP WebSocket — no auth required (chargepoints authenticate via OCPP).
	r.Get("/ocpp/{chargepoint_id}", deps.OCPPServer.HandleWebSocket)

	r.Route("/api/v1", func(r chi.Router) {
		// Auth routes (public with rate limiting).
		r.Group(func(r chi.Router) {
			r.Use(mw.AuthRateLimit())
			r.Post("/auth/register", authH.Register)
			r.Post("/auth/login", authH.Login)
			r.Post("/auth/apple", authH.AppleAuth)
			r.Post("/auth/refresh", authH.Refresh)
		})

		// Auth routes requiring JWT.
		r.Group(func(r chi.Router) {
			r.Use(authMiddleware)
			r.Post("/auth/logout", authH.Logout)
		})

		// User routes (authenticated).
		r.Group(func(r chi.Router) {
			r.Use(authMiddleware)
			r.Use(mw.APIRateLimit())
			r.Get("/users/me", usersH.GetMe)
			r.Put("/users/me", usersH.UpdateMe)
			r.Delete("/users/me", usersH.DeleteMe)
			r.Put("/users/me/role", usersH.UpdateRole)
		})

		// Wallbox routes.
		r.Group(func(r chi.Router) {
			r.Use(mw.APIRateLimit())

			// Public.
			r.Get("/wallboxes", wallboxH.List)
			r.Get("/wallboxes/{id}", wallboxH.GetByID)
			r.Get("/wallboxes/{id}/reviews", reviewsH.ListForWallbox)
			r.Get("/wallboxes/{id}/availability", wallboxH.GetAvailability)
			r.Get("/wallboxes/{id}/slots", wallboxH.GetSlots)

			// Authenticated.
			r.Group(func(r chi.Router) {
				r.Use(authMiddleware)
				r.Post("/wallboxes", wallboxH.Create)
				r.Put("/wallboxes/{id}", wallboxH.Update)
				r.Delete("/wallboxes/{id}", wallboxH.Delete)
				r.Put("/wallboxes/{id}/status", wallboxH.UpdateStatus)
				r.Put("/wallboxes/{id}/availability", wallboxH.UpdateAvailability)
			})
		})

		// Booking routes (authenticated).
		r.Group(func(r chi.Router) {
			r.Use(authMiddleware)
			r.Use(mw.APIRateLimit())
			r.Get("/bookings", bookingsH.List)
			r.Post("/bookings", bookingsH.Create)
			r.Get("/bookings/{id}", bookingsH.GetByID)
			r.Delete("/bookings/{id}", bookingsH.Cancel)
			r.Post("/bookings/{id}/confirm-payment", bookingsH.ConfirmPayment)
		})

		// Review creation (authenticated).
		r.Group(func(r chi.Router) {
			r.Use(authMiddleware)
			r.Use(mw.APIRateLimit())
			r.Post("/reviews", reviewsH.Create)
		})

		// Payment routes.
		r.Group(func(r chi.Router) {
			// Webhook is public (verified by Stripe signature).
			r.Post("/payments/webhook", paymentsH.Webhook)

			// Onboarding requires auth.
			r.Group(func(r chi.Router) {
				r.Use(authMiddleware)
				r.Use(mw.APIRateLimit())
				r.Post("/payments/onboard", paymentsH.Onboard)
				r.Get("/payments/onboard/return", paymentsH.OnboardReturn)
			})
		})

		// Host routes (authenticated + host/both/admin role).
		r.Group(func(r chi.Router) {
			r.Use(authMiddleware)
			r.Use(mw.APIRateLimit())
			r.Use(mw.RequireRole("host", "both"))
			r.Get("/host/bookings", hostH.GetBookings)
			r.Get("/host/earnings", hostH.GetEarnings)
			r.Get("/host/sessions", hostH.GetSessions)
		})
	})

	return r
}
