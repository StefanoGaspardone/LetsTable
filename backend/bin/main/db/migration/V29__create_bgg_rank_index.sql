CREATE TABLE bgg_rank_indexes (
    bgg_id BIGINT PRIMARY KEY,
    rank INTEGER NOT NULL
);

CREATE INDEX idx_bgg_rank_index_rank ON bgg_rank_indexes (rank);