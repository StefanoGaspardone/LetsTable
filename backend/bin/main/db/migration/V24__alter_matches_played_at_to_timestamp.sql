ALTER TABLE matches
    ALTER COLUMN played_at TYPE timestamptz
    USING played_at::timestamptz;