ALTER TABLE users
    ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE match_expansions (
    match_id UUID NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    expansion_game_id UUID NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    PRIMARY KEY (match_id, expansion_game_id)
);

CREATE INDEX idx_match_expansions_match_id ON match_expansions (match_id);
CREATE INDEX idx_match_expansions_expansion_game_id ON match_expansions (expansion_game_id);