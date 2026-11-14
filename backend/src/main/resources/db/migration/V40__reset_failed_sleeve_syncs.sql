UPDATE games g
SET sleeves_synced_at = NULL
WHERE g.sleeves_synced_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM game_sleeves s WHERE s.game_id = g.id);