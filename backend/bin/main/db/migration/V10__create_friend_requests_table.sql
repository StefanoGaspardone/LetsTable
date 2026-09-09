CREATE TABLE friend_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_friend_request_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT chk_friend_request_not_self CHECK (sender_id != receiver_id),
    CONSTRAINT uq_friend_request_pair UNIQUE (sender_id, receiver_id)
);

CREATE INDEX idx_friend_requests_sender ON friend_requests (sender_id);
CREATE INDEX idx_friend_requests_receiver ON friend_requests (receiver_id);
CREATE INDEX idx_friend_requests_status ON friend_requests (status);