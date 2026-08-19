CREATE TABLE hot_games (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bgg_id BIGINT NOT NULL,
    rank INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    thumbnail_url TEXT,
    year_published INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_hot_games_rank ON hot_games (rank);