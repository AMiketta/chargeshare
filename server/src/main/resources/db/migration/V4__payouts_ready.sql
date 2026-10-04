-- Hosts können ihre Wallboxes erst buchbar machen, wenn das Stripe-Auszahlungskonto vollständig eingerichtet ist.
-- Bestehende Hosts starten mit `false`; der Server prüft das Konto bei Stripe und setzt den Wert dann auf `true`.
ALTER TABLE users ADD COLUMN IF NOT EXISTS payouts_ready BOOLEAN NOT NULL DEFAULT FALSE;
