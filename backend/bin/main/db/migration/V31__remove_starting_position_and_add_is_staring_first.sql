ALTER TABLE match_players ADD COLUMN is_starting_first BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE match_players DROP COLUMN starting_position;

ALTER TABLE match_teams ADD COLUMN is_starting_first BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE match_teams DROP COLUMN starting_position;