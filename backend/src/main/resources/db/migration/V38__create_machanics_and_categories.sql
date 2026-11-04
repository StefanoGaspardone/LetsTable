CREATE TABLE mechanics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bgg_id BIGINT NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    name_it VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bgg_id BIGINT NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    name_it VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE game_mechanics (
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    mechanic_id UUID NOT NULL REFERENCES mechanics(id) ON DELETE CASCADE,
    PRIMARY KEY (game_id, mechanic_id)
);

CREATE TABLE game_categories (
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (game_id, category_id)
);

CREATE INDEX idx_game_mechanics_mechanic_id ON game_mechanics(mechanic_id);
CREATE INDEX idx_game_categories_category_id ON game_categories(category_id);

UPDATE games SET last_synced_at = 'epoch';