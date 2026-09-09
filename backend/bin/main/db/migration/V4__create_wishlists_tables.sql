CREATE TABLE wishlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    is_shared BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wishlists_owner ON wishlists (owner_id);

CREATE TABLE wishlist_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wishlist_id UUID NOT NULL REFERENCES wishlists (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_wishlist_member UNIQUE (wishlist_id, user_id)
);

CREATE INDEX idx_wishlist_members_wishlist ON wishlist_members (wishlist_id);
CREATE INDEX idx_wishlist_members_user ON wishlist_members (user_id);

CREATE TABLE wishlist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wishlist_id UUID NOT NULL REFERENCES wishlists (id) ON DELETE CASCADE,
    game_id UUID NOT NULL REFERENCES games (id) ON DELETE RESTRICT,
    added_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_wishlist_item UNIQUE (wishlist_id, game_id)
);

CREATE INDEX idx_wishlist_items_wishlist ON wishlist_items (wishlist_id);