CREATE TABLE game_sleeves (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    name VARCHAR(255),
    height INTEGER,
    width INTEGER,
    quantity INTEGER,
    quantity_note VARCHAR(255)
);

CREATE INDEX idx_game_sleeves_game_id ON game_sleeves (game_id);