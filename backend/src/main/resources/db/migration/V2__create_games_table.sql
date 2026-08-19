CREATE TABLE games (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bgg_id BIGINT NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    year_published INTEGER,
    thumbnail_url TEXT,
    image_url TEXT,
    min_players INTEGER,
    max_players INTEGER,
    playing_time_minutes INTEGER,
    description TEXT,
    last_synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_games_name ON games (name);