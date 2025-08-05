-- Add updated_at timestamp column to rmas table
ALTER TABLE rmas ADD COLUMN updated_at TIMESTAMP;

-- Set updatedAt to current timestamp for existing RMAs
UPDATE rmas SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;

-- Add index for better performance on sorting
CREATE INDEX idx_rmas_updated_at ON rmas(updated_at);