CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games (id) ON DELETE RESTRICT,
    created_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    is_team_based BOOLEAN NOT NULL DEFAULT FALSE,
    played_at DATE NOT NULL,
    place VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_matches_game ON matches (game_id);
CREATE INDEX idx_matches_created_by ON matches (created_by_user_id);

CREATE TABLE match_teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    name VARCHAR(50),
    color VARCHAR(20) NOT NULL,
    score INTEGER NOT NULL DEFAULT 0,
    is_winner BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_match_teams_match ON match_teams (match_id);

CREATE TABLE match_players (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    team_id UUID REFERENCES match_teams (id) ON DELETE CASCADE,
    user_id UUID REFERENCES users (id) ON DELETE RESTRICT,
    guest_name VARCHAR(100),
    color VARCHAR(20),
    score INTEGER,
    is_winner BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_match_player_identity CHECK (
       (user_id IS NOT NULL AND guest_name IS NULL) OR
       (user_id IS NULL AND guest_name IS NOT NULL)
       )
);

CREATE INDEX idx_match_players_match ON match_players (match_id);
CREATE INDEX idx_match_players_user ON match_players (user_id);
CREATE INDEX idx_match_players_team ON match_players (team_id);