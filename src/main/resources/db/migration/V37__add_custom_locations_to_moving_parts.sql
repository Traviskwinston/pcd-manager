-- Add custom location fields to moving_parts table
ALTER TABLE moving_parts ADD COLUMN from_custom_location VARCHAR(255);
ALTER TABLE moving_parts ADD COLUMN to_custom_locations TEXT;

