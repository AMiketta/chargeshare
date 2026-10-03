package services

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/stripe/stripe-go/v79"
	"go.uber.org/zap"

	"github.com/chargeschare/backend/internal/models"
	"github.com/chargeschare/backend/pkg/apperror"
)

// BookingService handles booking business logic.
type BookingService struct {
	db             *pgxpool.Pool
	paymentService *PaymentService
	log            *zap.Logger
}

// NewBookingService creates a new BookingService.
func NewBookingService(db *pgxpool.Pool, paymentService *PaymentService, log *zap.Logger) *BookingService {
	return &BookingService{
		db:             db,
		paymentService: paymentService,
		log:            log,
	}
}

// CheckAvailability returns true if the wallbox is available during the requested window.
func (s *BookingService) CheckAvailability(ctx context.Context, wallboxID uuid.UUID, start, end time.Time) (bool, error) {
	var count int
	err := s.db.QueryRow(ctx,
		`SELECT COUNT(*) FROM bookings
		  WHERE wallbox_id = $1
		    AND status NOT IN ('cancelled','failed')
		    AND tstzrange(start_time, end_time) && tstzrange($2, $3)`,
		wallboxID, start, end,
	).Scan(&count)
	if err != nil {
		return false, fmt.Errorf("check availability: %w", err)
	}
	return count == 0, nil
}

// EstimateKwh estimates energy consumption based on power and duration.
func EstimateKwh(powerKw, durationHours float64) float64 {
	return powerKw * durationHours
}

// EstimateCost estimates the total cost.
func EstimateCost(kwh, pricePerKwh float64) float64 {
	return kwh * pricePerKwh
}

// CreateBookingWithPayment creates a booking and a Stripe PaymentIntent.
func (s *BookingService) CreateBookingWithPayment(
	ctx context.Context,
	wallboxID uuid.UUID,
	driverID uuid.UUID,
	startTime, endTime time.Time,
	stripeCustomerID string,
) (*models.Booking, *stripe.PaymentIntent, error) {

	// Fetch wallbox details.
	var wallbox models.Wallbox
	var availabilityRaw []byte
	err := s.db.QueryRow(ctx,
		`SELECT id, owner_id, power_kw, price_per_kwh, stripe_account_id,
		        min_duration_min, max_duration_min, is_online
		   FROM wallboxes w
		   JOIN users u ON u.id = w.owner_id
		  WHERE w.id = $1`,
		wallboxID,
	).Scan(
		&wallbox.ID, &wallbox.OwnerID, &wallbox.PowerKW, &wallbox.PricePerKwh,
		&wallbox.OwnerID, // owner's stripe_account_id comes from user join
		&wallbox.MinDurationMin, &wallbox.MaxDurationMin, &wallbox.IsOnline,
	)
	_ = availabilityRaw
	if err != nil {
		return nil, nil, apperror.NotFound("wallbox not found")
	}

	if !wallbox.IsOnline {
		return nil, nil, apperror.BadRequest("wallbox is currently offline")
	}

	// Check duration constraints.
	durationMin := int(endTime.Sub(startTime).Minutes())
	if durationMin < wallbox.MinDurationMin {
		return nil, nil, apperror.ValidationError(fmt.Sprintf("minimum booking duration is %d minutes", wallbox.MinDurationMin))
	}
	if durationMin > wallbox.MaxDurationMin {
		return nil, nil, apperror.ValidationError(fmt.Sprintf("maximum booking duration is %d minutes", wallbox.MaxDurationMin))
	}

	// Check availability.
	available, err := s.CheckAvailability(ctx, wallboxID, startTime, endTime)
	if err != nil {
		return nil, nil, apperror.Internal("availability check failed")
	}
	if !available {
		return nil, nil, apperror.Conflict("wallbox is not available for the requested time slot")
	}

	// Calculate costs.
	durationHours := endTime.Sub(startTime).Hours()
	estKwh := EstimateKwh(wallbox.PowerKW, durationHours)
	estCost := EstimateCost(estKwh, wallbox.PricePerKwh)

	// Get host's Stripe account ID.
	var hostStripeAccountID *string
	err = s.db.QueryRow(ctx,
		`SELECT stripe_account_id FROM users WHERE id = $1`,
		wallbox.OwnerID,
	).Scan(&hostStripeAccountID)
	if err != nil {
		return nil, nil, apperror.Internal("host account not found")
	}

	connectedAccount := ""
	if hostStripeAccountID != nil {
		connectedAccount = *hostStripeAccountID
	}

	// Amount in cents.
	amountCents := int64(estCost * 100)
	if amountCents < 50 {
		amountCents = 50 // Stripe minimum
	}

	// Create Stripe PaymentIntent.
	pi, err := s.paymentService.CreatePaymentIntent(amountCents, "eur", stripeCustomerID, connectedAccount, s.paymentService.platformFeePercent)
	if err != nil {
		return nil, nil, apperror.Internal("payment intent creation failed")
	}

	// Insert booking.
	booking := &models.Booking{
		WallboxID:             wallboxID,
		DriverID:              driverID,
		StartTime:             startTime,
		EndTime:               endTime,
		Status:                models.BookingPending,
		PricePerKwh:           wallbox.PricePerKwh,
		EstimatedKwh:          &estKwh,
		TotalAmount:           &estCost,
		StripePaymentIntentID: &pi.ID,
	}

	err = s.db.QueryRow(ctx,
		`INSERT INTO bookings (wallbox_id, driver_id, start_time, end_time, status,
		                       estimated_kwh, price_per_kwh, total_amount, stripe_payment_intent_id)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		 RETURNING id, created_at, updated_at`,
		booking.WallboxID, booking.DriverID, booking.StartTime, booking.EndTime,
		booking.Status, booking.EstimatedKwh, booking.PricePerKwh,
		booking.TotalAmount, booking.StripePaymentIntentID,
	).Scan(&booking.ID, &booking.CreatedAt, &booking.UpdatedAt)
	if err != nil {
		s.log.Error("insert booking failed", zap.Error(err))
		return nil, nil, apperror.Internal("could not create booking")
	}

	return booking, pi, nil
}

// GetBookingByID retrieves a single booking by ID.
func (s *BookingService) GetBookingByID(ctx context.Context, bookingID uuid.UUID) (*models.Booking, error) {
	var b models.Booking
	err := s.db.QueryRow(ctx,
		`SELECT id, wallbox_id, driver_id, start_time, end_time, status,
		        estimated_kwh, actual_kwh, price_per_kwh, total_amount,
		        stripe_payment_intent_id, stripe_charge_id, ocpp_transaction_id,
		        cancel_reason, created_at, updated_at
		   FROM bookings WHERE id = $1`,
		bookingID,
	).Scan(
		&b.ID, &b.WallboxID, &b.DriverID, &b.StartTime, &b.EndTime, &b.Status,
		&b.EstimatedKwh, &b.ActualKwh, &b.PricePerKwh, &b.TotalAmount,
		&b.StripePaymentIntentID, &b.StripeChargeID, &b.OcppTransactionID,
		&b.CancelReason, &b.CreatedAt, &b.UpdatedAt,
	)
	if err != nil {
		return nil, apperror.NotFound("booking not found")
	}
	return &b, nil
}

// CancelBooking cancels a booking and triggers a refund if within policy.
func (s *BookingService) CancelBooking(ctx context.Context, bookingID, userID uuid.UUID, reason *string) error {
	booking, err := s.GetBookingByID(ctx, bookingID)
	if err != nil {
		return err
	}

	// Only the driver or admin can cancel.
	if booking.DriverID != userID {
		return apperror.Forbidden("only the booking owner can cancel")
	}

	if booking.Status == models.BookingCancelled || booking.Status == models.BookingCompleted {
		return apperror.BadRequest("booking cannot be cancelled in its current state")
	}

	// Refund if more than 30 minutes before start.
	shouldRefund := time.Now().UTC().Before(booking.StartTime.Add(-30 * time.Minute))
	if shouldRefund && booking.StripePaymentIntentID != nil {
		if err := s.paymentService.RefundPaymentIntent(ctx, *booking.StripePaymentIntentID); err != nil {
			s.log.Warn("refund failed", zap.Error(err), zap.String("booking_id", bookingID.String()))
		}
	}

	_, err = s.db.Exec(ctx,
		`UPDATE bookings SET status = 'cancelled', cancel_reason = $2, updated_at = NOW() WHERE id = $1`,
		bookingID, reason,
	)
	return err
}

// ConfirmPayment moves a booking from pending to confirmed after Stripe 3DS.
func (s *BookingService) ConfirmPayment(ctx context.Context, bookingID, userID uuid.UUID) (*models.Booking, error) {
	booking, err := s.GetBookingByID(ctx, bookingID)
	if err != nil {
		return nil, err
	}
	if booking.DriverID != userID {
		return nil, apperror.Forbidden("not your booking")
	}
	if booking.Status != models.BookingPending {
		return nil, apperror.BadRequest("booking is not in pending state")
	}

	_, err = s.db.Exec(ctx,
		`UPDATE bookings SET status = 'confirmed', updated_at = NOW() WHERE id = $1`,
		bookingID,
	)
	if err != nil {
		return nil, apperror.Internal("could not confirm booking")
	}
	booking.Status = models.BookingConfirmed
	return booking, nil
}
