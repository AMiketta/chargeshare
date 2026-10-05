-- Push-Gerätetokens (FCM/APNs). Ein Token gehört zu genau einem Nutzer; erneute Registrierung überschreibt user_id/platform.
CREATE TABLE IF NOT EXISTS push_devices (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform    TEXT NOT NULL CHECK (platform IN ('android', 'ios')),
  token       TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT push_devices_token_unique UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS push_devices_user_idx ON push_devices (user_id);
