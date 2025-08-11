-- Create join table between track_trends and rmas for related associations
CREATE TABLE IF NOT EXISTS tracktrend_rmas (
    tracktrend_id BIGINT NOT NULL,
    rma_id BIGINT NOT NULL,
    PRIMARY KEY (tracktrend_id, rma_id)
);

-- Add indexes to improve lookup performance
CREATE INDEX IF NOT EXISTS idx_tracktrend_rmas_tracktrend_id ON tracktrend_rmas (tracktrend_id);
CREATE INDEX IF NOT EXISTS idx_tracktrend_rmas_rma_id ON tracktrend_rmas (rma_id);

-- Note: Foreign keys omitted to support H2/Postgres dual-run without schema differences
