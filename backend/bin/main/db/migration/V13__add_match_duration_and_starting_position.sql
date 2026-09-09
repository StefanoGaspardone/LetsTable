ALTER TABLE matches ADD COLUMN duration_minutes INTEGER;

ALTER TABLE match_teams ADD COLUMN starting_position INTEGER;
ALTER TABLE match_players ADD COLUMN starting_position INTEGER;