ALTER TABLE games
    ADD COLUMN best_with VARCHAR(255),
    ADD COLUMN recommended_with VARCHAR(255),
    ADD COLUMN expansion_bgg_ids TEXT;