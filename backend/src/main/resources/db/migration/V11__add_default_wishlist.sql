ALTER TABLE wishlists ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_wishlist_default_per_owner ON wishlists (owner_id) WHERE is_default = TRUE;