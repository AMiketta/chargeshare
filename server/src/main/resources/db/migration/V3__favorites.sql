-- Favoriten: ein Fahrer markiert Wallboxen mit dem Herz. Wird mit dem Nutzer oder der Wallbox gelöscht.
CREATE TABLE IF NOT EXISTS favorites (
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  wallbox_id  UUID NOT NULL REFERENCES wallboxes(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, wallbox_id)
);

CREATE INDEX IF NOT EXISTS favorites_wallbox_idx ON favorites (wallbox_id);
