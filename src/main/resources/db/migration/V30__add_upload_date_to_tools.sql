-- Add upload_date column to tools table
ALTER TABLE tools ADD COLUMN upload_date DATE;

-- Create index for sorting performance
CREATE INDEX idx_tools_upload_date ON tools(upload_date);
