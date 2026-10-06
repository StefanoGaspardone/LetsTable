CREATE TABLE user_achievements (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    achievement_code VARCHAR(64)  NOT NULL,
    unlocked_at      TIMESTAMPTZ  NOT NULL,
    seen_at          TIMESTAMPTZ  NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_achievements_user_code UNIQUE (user_id, achievement_code)
);

CREATE INDEX idx_user_achievements_unseen
    ON user_achievements (user_id)
    WHERE seen_at IS NULL;