# ChargeShare Backend

P2P Wallbox-Sharing platform backend — Go 1.22, PostgreSQL 16, OCPP 1.6.

## Prerequisites

- Go 1.22+
- PostgreSQL 16 (or Docker)
- [golang-migrate](https://github.com/golang-migrate/migrate) CLI (for manual migrations)

## Quick Start

```bash
# Copy and configure environment variables
cp .env.example .env
# Edit .env with your database URL, JWT secrets, and Stripe keys

# Start database (Docker)
make docker-up

# Run the API (migrations run automatically on startup)
make run
```

## Docker

```bash
# Build and start all services
make docker-up

# Stop all services
make docker-down
```

## Configuration (.env)

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | ✅ | PostgreSQL connection string |
| `JWT_SECRET` | ✅ | HS256 signing secret for access tokens (≥32 chars) |
| `JWT_REFRESH_SECRET` | ✅ | Signing secret for refresh token signing |
| `STRIPE_SECRET_KEY` | ✅ | Stripe secret key (`sk_test_...`) |
| `STRIPE_WEBHOOK_SECRET` | ✅ | Stripe webhook signing secret (`whsec_...`) |
| `STRIPE_PLATFORM_FEE_PERCENT` | — | Platform fee % (default: 15) |
| `PORT` | — | HTTP port (default: 8080) |
| `ENV` | — | `development` or `production` |
| `LOG_LEVEL` | — | `debug`, `info`, `warn`, `error` |
| `APPLE_BUNDLE_ID` | — | iOS bundle ID for Apple Sign In |

## API Endpoints

### Authentication (public)
```
POST /api/v1/auth/register      — email+password registration
POST /api/v1/auth/login         — email+password login
POST /api/v1/auth/apple         — Sign in with Apple
POST /api/v1/auth/refresh       — Rotate JWT tokens
POST /api/v1/auth/logout        — Revoke refresh token
```

### Users (Bearer token required)
```
GET    /api/v1/users/me         — Get current user
PUT    /api/v1/users/me         — Update profile
DELETE /api/v1/users/me         — Delete account
PUT    /api/v1/users/me/role    — Become a host
```

### Wallboxes
```
GET    /api/v1/wallboxes                        — Search (geo-filter, connector, price)
POST   /api/v1/wallboxes                        — Create (auth)
GET    /api/v1/wallboxes/:id                    — Detail
PUT    /api/v1/wallboxes/:id                    — Update (owner)
DELETE /api/v1/wallboxes/:id                    — Delete (owner)
PUT    /api/v1/wallboxes/:id/status             — Toggle online/offline
GET    /api/v1/wallboxes/:id/availability       — Weekly schedule
PUT    /api/v1/wallboxes/:id/availability       — Update schedule
GET    /api/v1/wallboxes/:id/slots?date=YYYY-MM-DD — Available time slots
GET    /api/v1/wallboxes/:id/reviews            — Paginated reviews
```

### Bookings (auth)
```
GET    /api/v1/bookings                 — List your bookings
POST   /api/v1/bookings                 — Create + Stripe PaymentIntent
GET    /api/v1/bookings/:id             — Detail
DELETE /api/v1/bookings/:id             — Cancel (+ refund)
POST   /api/v1/bookings/:id/confirm-payment — Confirm after 3DS
```

### Host Dashboard (auth + host role)
```
GET    /api/v1/host/bookings    — Incoming bookings
GET    /api/v1/host/earnings    — Revenue summary
GET    /api/v1/host/sessions    — Recent sessions with driver info
```

### Reviews (auth)
```
POST   /api/v1/reviews          — Submit review (completed booking only)
```

### Payments
```
POST   /api/v1/payments/onboard         — Stripe Connect onboarding link
GET    /api/v1/payments/onboard/return  — Post-onboarding callback
POST   /api/v1/payments/webhook         — Stripe webhook (sig-verified)
```

### OCPP 1.6 WebSocket
```
GET    /ocpp/:chargepoint_id    — Chargepoint WebSocket connection
```

Supported OCPP actions: `BootNotification`, `Heartbeat`, `Authorize`,
`StartTransaction`, `StopTransaction`, `MeterValues`, `StatusNotification`.

## Architecture

```
cmd/api/main.go          — Entry point, DI, graceful shutdown
internal/config/         — Environment-based config
internal/db/             — pgxpool setup, embedded SQL migrations
internal/models/         — Domain types with JSON/DB tags
internal/services/       — Business logic (auth, booking, payment, wallbox)
internal/api/            — chi router, middleware, HTTP handlers
internal/ocpp/           — OCPP 1.6 WebSocket server
pkg/apperror/            — Structured JSON error responses
```

## Database Schema

The migration creates:
- `users` — drivers and hosts
- `wallboxes` — charging stations with geo-indexed location
- `bookings` — reservations with EXCLUDE constraint preventing overlaps
- `reviews` — post-session ratings
- `charging_sessions` — OCPP telemetry
- `refresh_tokens` — hashed long-lived tokens

Extensions required: `pgcrypto`, `cube`, `earthdistance`, `btree_gist`

## Testing

```bash
make test
```

Integration tests in `internal/api/handlers/` skip automatically when no database is available.
