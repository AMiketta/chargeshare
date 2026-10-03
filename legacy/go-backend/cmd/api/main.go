package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-playground/validator/v10"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"

	"github.com/chargeschare/backend/internal/api"
	"github.com/chargeschare/backend/internal/config"
	"github.com/chargeschare/backend/internal/db"
	"github.com/chargeschare/backend/internal/ocpp"
	"github.com/chargeschare/backend/internal/services"
)

func main() {
	// --- Logger ---
	log := buildLogger("info")
	defer log.Sync()

	// --- Config ---
	cfg, err := config.Load()
	if err != nil {
		log.Fatal("failed to load config", zap.Error(err))
	}

	// Rebuild logger with configured level.
	log = buildLogger(cfg.LogLevel)
	defer log.Sync()

	log.Info("starting ChargeShare API", zap.String("env", cfg.Env), zap.String("port", cfg.Port))

	// --- Database ---
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	pool, err := db.NewPool(ctx, cfg.DatabaseURL, log)
	if err != nil {
		log.Fatal("failed to connect to database", zap.Error(err))
	}
	defer pool.Close()

	// Run migrations.
	if err := db.RunMigrations(cfg.DatabaseURL, log); err != nil {
		log.Fatal("failed to run migrations", zap.Error(err))
	}

	// --- Services ---
	authSvc := services.NewAuthService(
		pool,
		cfg.JWTSecret,
		cfg.JWTRefreshSecret,
		cfg.AppleBundleID,
		log,
	)

	paymentSvc := services.NewPaymentService(
		cfg.StripeSecretKey,
		cfg.StripeWebhookSecret,
		cfg.StripePlatformFeePercent,
		pool,
		log,
	)

	wallboxSvc := services.NewWallboxService(pool, log)
	bookingSvc := services.NewBookingService(pool, paymentSvc, log)

	// --- OCPP ---
	ocppHandler := ocpp.NewMessageHandler(pool, log)
	ocppServer := ocpp.NewOCPPServer(ocppHandler, log)

	// --- Validator ---
	validate := validator.New()

	// --- Router ---
	router := api.NewRouter(api.RouterDeps{
		DB:         pool,
		AuthSvc:    authSvc,
		BookingSvc: bookingSvc,
		PaymentSvc: paymentSvc,
		WallboxSvc: wallboxSvc,
		OCPPServer: ocppServer,
		Log:        log,
		Validate:   validate,
	})

	// --- HTTP Server ---
	srv := &http.Server{
		Addr:         fmt.Sprintf(":%s", cfg.Port),
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Start server in background.
	go func() {
		log.Info("HTTP server listening", zap.String("addr", srv.Addr))
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatal("server error", zap.Error(err))
		}
	}()

	// --- Graceful Shutdown ---
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGTERM, syscall.SIGINT)
	<-quit

	log.Info("shutting down server...")
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer shutdownCancel()

	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Error("server shutdown error", zap.Error(err))
	}

	log.Info("server stopped")
}

// buildLogger creates a zap logger with the given log level.
func buildLogger(level string) *zap.Logger {
	var zapLevel zapcore.Level
	switch level {
	case "debug":
		zapLevel = zapcore.DebugLevel
	case "warn":
		zapLevel = zapcore.WarnLevel
	case "error":
		zapLevel = zapcore.ErrorLevel
	default:
		zapLevel = zapcore.InfoLevel
	}

	cfg := zap.NewProductionConfig()
	cfg.Level = zap.NewAtomicLevelAt(zapLevel)
	cfg.EncoderConfig.TimeKey = "ts"
	cfg.EncoderConfig.EncodeTime = zapcore.ISO8601TimeEncoder

	log, err := cfg.Build()
	if err != nil {
		panic(fmt.Sprintf("failed to build logger: %v", err))
	}
	return log
}
