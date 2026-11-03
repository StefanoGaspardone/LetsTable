ALTER TABLE matches ADD COLUMN winner_score INTEGER;

UPDATE matches m
SET winner_score = w.score FROM (
        SELECT match_id, MAX(score) AS score
        FROM match_players
        WHERE is_winner = true AND team_id IS NULL
        GROUP BY match_id
        UNION ALL
        SELECT match_id, MAX(score) AS score
        FROM match_teams
        WHERE is_winner = true
        GROUP BY match_id
    ) w
WHERE w.match_id = m.id
  AND m.duration_minutes IS NOT NULL;