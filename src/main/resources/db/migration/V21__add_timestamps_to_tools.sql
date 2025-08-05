-- Add created_at and updated_at timestamp columns to tools table
ALTER TABLE tools ADD COLUMN created_at TIMESTAMP;
ALTER TABLE tools ADD COLUMN updated_at TIMESTAMP;

-- Set default values for existing tools
-- Use setDate as createdAt if available, otherwise use current timestamp
UPDATE tools SET created_at = COALESCE(
    (setDate || ' 00:00:00')::timestamp,
    CURRENT_TIMESTAMP
) WHERE created_at IS NULL;

-- Set updatedAt to current timestamp for existing tools
UPDATE tools SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;

-- Add indexes for better performance on sorting
CREATE INDEX idx_tools_created_at ON tools(created_at);
CREATE INDEX idx_tools_updated_at ON tools(updated_at);