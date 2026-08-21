CREATE TABLE push_tokens (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
     token VARCHAR(255) NOT NULL UNIQUE,
     device_name VARCHAR(100),
     created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_push_tokens_user ON push_tokens (user_id);