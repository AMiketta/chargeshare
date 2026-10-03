package services

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/stripe/stripe-go/v79"
	"github.com/stripe/stripe-go/v79/client"
	"github.com/stripe/stripe-go/v79/webhook"
	"go.uber.org/zap"
)

// PaymentService wraps Stripe operations.
type PaymentService struct {
	stripe             *client.API
	db                 *pgxpool.Pool
	webhookSecret      string
	platformFeePercent int64
	log                *zap.Logger
}

// NewPaymentService creates a new PaymentService.
func NewPaymentService(secretKey, webhookSecret string, platformFeePercent int64, db *pgxpool.Pool, log *zap.Logger) *PaymentService {
	sc := &client.API{}
	sc.Init(secretKey, nil)
	return &PaymentService{
		stripe:             sc,
		db:                 db,
		webhookSecret:      webhookSecret,
		platformFeePercent: platformFeePercent,
		log:                log,
	}
}

// CreatePaymentIntent creates a Stripe PaymentIntent with optional Connect routing.
func (s *PaymentService) CreatePaymentIntent(
	amount int64,
	currency string,
	customerID string,
	connectedAccountID string,
	platformFeePercent int64,
) (*stripe.PaymentIntent, error) {
	params := &stripe.PaymentIntentParams{
		Amount:   stripe.Int64(amount),
		Currency: stripe.String(currency),
	}
	if customerID != "" {
		params.Customer = stripe.String(customerID)
	}
	params.PaymentMethodTypes = stripe.StringSlice([]string{"card"})

	if connectedAccountID != "" {
		feeAmount := amount * platformFeePercent / 100
		params.ApplicationFeeAmount = stripe.Int64(feeAmount)
		params.TransferData = &stripe.PaymentIntentTransferDataParams{
			Destination: stripe.String(connectedAccountID),
		}
	}

	pi, err := s.stripe.PaymentIntents.New(params)
	if err != nil {
		return nil, fmt.Errorf("create payment intent: %w", err)
	}
	return pi, nil
}

// CreateStripeCustomer creates a Stripe customer for a new user.
func (s *PaymentService) CreateStripeCustomer(email, name string) (*stripe.Customer, error) {
	params := &stripe.CustomerParams{
		Email: stripe.String(email),
		Name:  stripe.String(name),
	}
	cust, err := s.stripe.Customers.New(params)
	if err != nil {
		return nil, fmt.Errorf("create stripe customer: %w", err)
	}
	return cust, nil
}

// CreateConnectOnboardingLink creates or retrieves a Stripe Connect Express account
// and returns an account link URL for onboarding.
func (s *PaymentService) CreateConnectOnboardingLink(ctx context.Context, userID, email string, existingAccountID *string) (string, error) {
	var accountID string

	if existingAccountID != nil && *existingAccountID != "" {
		accountID = *existingAccountID
	} else {
		// Create a new Express account.
		params := &stripe.AccountParams{
			Type:  stripe.String(string(stripe.AccountTypeExpress)),
			Email: stripe.String(email),
		}
		acct, err := s.stripe.Accounts.New(params)
		if err != nil {
			return "", fmt.Errorf("create stripe connect account: %w", err)
		}
		accountID = acct.ID

		// Persist the account ID.
		_, err = s.db.Exec(ctx,
			`UPDATE users SET stripe_account_id = $1, updated_at = NOW() WHERE id = $2`,
			accountID, userID,
		)
		if err != nil {
			s.log.Error("failed to save stripe account id", zap.Error(err))
		}
	}

	// Create account link for onboarding flow.
	linkParams := &stripe.AccountLinkParams{
		Account:    stripe.String(accountID),
		RefreshURL: stripe.String("https://chargeschare.de/host/onboard/refresh"),
		ReturnURL:  stripe.String("https://chargeschare.de/host/onboard/return"),
		Type:       stripe.String("account_onboarding"),
	}
	link, err := s.stripe.AccountLinks.New(linkParams)
	if err != nil {
		return "", fmt.Errorf("create account link: %w", err)
	}
	return link.URL, nil
}

// RefundPaymentIntent issues a full refund for a PaymentIntent.
func (s *PaymentService) RefundPaymentIntent(ctx context.Context, paymentIntentID string) error {
	params := &stripe.RefundParams{
		PaymentIntent: stripe.String(paymentIntentID),
	}
	_, err := s.stripe.Refunds.New(params)
	if err != nil {
		return fmt.Errorf("create refund: %w", err)
	}
	return nil
}

// HandleWebhook verifies and processes incoming Stripe webhook events.
func (s *PaymentService) HandleWebhook(payload []byte, sigHeader string) error {
	event, err := webhook.ConstructEvent(payload, sigHeader, s.webhookSecret)
	if err != nil {
		return fmt.Errorf("webhook signature verification failed: %w", err)
	}

	s.log.Info("stripe webhook received", zap.String("type", string(event.Type)))

	switch event.Type {
	case "payment_intent.succeeded":
		var pi stripe.PaymentIntent
		if err := json.Unmarshal(event.Data.Raw, &pi); err != nil {
			return err
		}
		return s.handlePaymentIntentSucceeded(context.Background(), &pi)

	case "payment_intent.payment_failed":
		var pi stripe.PaymentIntent
		if err := json.Unmarshal(event.Data.Raw, &pi); err != nil {
			return err
		}
		return s.handlePaymentIntentFailed(context.Background(), &pi)

	case "charge.refunded":
		var charge stripe.Charge
		if err := json.Unmarshal(event.Data.Raw, &charge); err != nil {
			return err
		}
		return s.handleChargeRefunded(context.Background(), &charge)
	}

	return nil
}

func (s *PaymentService) handlePaymentIntentSucceeded(ctx context.Context, pi *stripe.PaymentIntent) error {
	_, err := s.db.Exec(ctx,
		`UPDATE bookings
		    SET status = 'confirmed', updated_at = NOW()
		  WHERE stripe_payment_intent_id = $1 AND status = 'pending'`,
		pi.ID,
	)
	if err != nil {
		s.log.Error("update booking on payment success", zap.Error(err))
	}
	return err
}

func (s *PaymentService) handlePaymentIntentFailed(ctx context.Context, pi *stripe.PaymentIntent) error {
	_, err := s.db.Exec(ctx,
		`UPDATE bookings
		    SET status = 'failed', updated_at = NOW()
		  WHERE stripe_payment_intent_id = $1 AND status = 'pending'`,
		pi.ID,
	)
	if err != nil {
		s.log.Error("update booking on payment failure", zap.Error(err))
	}
	return err
}

func (s *PaymentService) handleChargeRefunded(ctx context.Context, charge *stripe.Charge) error {
	if charge.PaymentIntent == nil {
		return nil
	}
	_, err := s.db.Exec(ctx,
		`UPDATE bookings
		    SET status = 'cancelled', updated_at = NOW()
		  WHERE stripe_payment_intent_id = $1`,
		charge.PaymentIntent.ID,
	)
	return err
}
