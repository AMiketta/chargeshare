-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "cube";
CREATE EXTENSION IF NOT EXISTS "earthdistance" CASCADE;
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- users
CREATE TABLE IF NOT EXISTS users (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email                TEXT UNIQUE NOT NULL,
  password_hash        TEXT,
  apple_sub            TEXT UNIQUE,
  name                 TEXT NOT NULL,
  phone                TEXT,
  role                 TEXT NOT NULL DEFAULT 'driver' CHECK (role IN ('driver','host','both','admin')),
  stripe_customer_id   TEXT,
  stripe_account_id    TEXT,
  avatar_url           TEXT,
  is_verified          BOOLEAN NOT NULL DEFAULT FALSE,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- wallboxes
CREATE TABLE IF NOT EXISTS wallboxes (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name             TEXT NOT NULL,
  address          TEXT NOT NULL,
  city             TEXT NOT NULL,
  postal_code      TEXT NOT NULL,
  lat              DOUBLE PRECISION NOT NULL,
  lng              DOUBLE PRECISION NOT NULL,
  power_kw         DECIMAL(5,2) NOT NULL,
  connector_type   TEXT NOT NULL CHECK (connector_type IN ('type2','ccs','chademo','type1')),
  price_per_kwh    DECIMAL(6,4) NOT NULL,
  min_duration_min INTEGER NOT NULL DEFAULT 30,
  max_duration_min INTEGER NOT NULL DEFAULT 480,
  is_online        BOOLEAN NOT NULL DEFAULT FALSE,
  is_metered       BOOLEAN NOT NULL DEFAULT FALSE,
  ocpp_id          TEXT UNIQUE,
  model            TEXT,
  manufacturer     TEXT,
  photos           TEXT[],
  availability     JSONB NOT NULL DEFAULT '{}',
  rating_avg       DECIMAL(3,2) DEFAULT 0,
  rating_count     INTEGER DEFAULT 0,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS wallboxes_location_idx ON wallboxes USING gist (ll_to_earth(lat, lng));
CREATE INDEX IF NOT EXISTS wallboxes_owner_idx ON wallboxes (owner_id);

-- bookings
CREATE TABLE IF NOT EXISTS bookings (
  id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wallbox_id                UUID NOT NULL REFERENCES wallboxes(id),
  driver_id                 UUID NOT NULL REFERENCES users(id),
  start_time                TIMESTAMPTZ NOT NULL,
  end_time                  TIMESTAMPTZ NOT NULL,
  status                    TEXT NOT NULL DEFAULT 'pending'
                            CHECK (status IN ('pending','confirmed','active','completed','cancelled','failed')),
  estimated_kwh             DECIMAL(8,3),
  actual_kwh                DECIMAL(8,3),
  price_per_kwh             DECIMAL(6,4) NOT NULL,
  total_amount              DECIMAL(10,2),
  stripe_payment_intent_id  TEXT,
  stripe_charge_id          TEXT,
  ocpp_transaction_id       INTEGER,
  cancel_reason             TEXT,
  created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT no_overlap EXCLUDE USING gist (
    wallbox_id WITH =,
    tstzrange(start_time, end_time) WITH &&
  ) WHERE (status NOT IN ('cancelled','failed'))
);

CREATE INDEX IF NOT EXISTS bookings_driver_idx ON bookings (driver_id);
CREATE INDEX IF NOT EXISTS bookings_wallbox_idx ON bookings (wallbox_id);

-- reviews
CREATE TABLE IF NOT EXISTS reviews (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id  UUID NOT NULL UNIQUE REFERENCES bookings(id),
  wallbox_id  UUID NOT NULL REFERENCES wallboxes(id),
  author_id   UUID NOT NULL REFERENCES users(id),
  rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment     TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS reviews_wallbox_idx ON reviews (wallbox_id);

-- charging_sessions
CREATE TABLE IF NOT EXISTS charging_sessions (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id     UUID REFERENCES bookings(id),
  ocpp_id        TEXT NOT NULL,
  transaction_id INTEGER,
  meter_start    INTEGER,
  meter_stop     INTEGER,
  started_at     TIMESTAMPTZ,
  stopped_at     TIMESTAMPTZ,
  stop_reason    TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS charging_sessions_booking_idx ON charging_sessions (booking_id);
CREATE INDEX IF NOT EXISTS charging_sessions_ocpp_idx ON charging_sessions (ocpp_id);

-- refresh_tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  TEXT NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS refresh_tokens_user_idx ON refresh_tokens (user_id);
