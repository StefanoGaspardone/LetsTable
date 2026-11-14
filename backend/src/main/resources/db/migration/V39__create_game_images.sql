ALTER TABLE games ADD COLUMN images_synced_at TIMESTAMPTZ;

CREATE TABLE game_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    bgg_image_id BIGINT NOT NULL,
    thumbnail_url TEXT NOT NULL,
    image_url TEXT NOT NULL,
    caption TEXT,
    author VARCHAR(255),
    page_url TEXT,
    position INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_game_images_game_bgg_image UNIQUE (game_id, bgg_image_id)
);

CREATE INDEX idx_game_images_game_id_position ON game_images (game_id, position);