-- Convert location from foreign key to simple string field
-- V20__convert_location_to_simple_field.sql

-- Add the new location_name column
ALTER TABLE tools ADD COLUMN location_name VARCHAR(255);

-- Populate location_name from existing location data
UPDATE tools 
SET location_name = (
    SELECT COALESCE(l.display_name, l.name, 'Unknown Location') 
    FROM locations l 
    WHERE l.id = tools.location_id
);

-- Set default for tools without location_id
UPDATE tools 
SET location_name = 'AZ F52' 
WHERE location_name IS NULL;

-- Make the column NOT NULL now that it's populated
ALTER TABLE tools ALTER COLUMN location_name SET NOT NULL;

-- We'll keep location_id for now in case we need to rollback
-- In a future migration, we can drop it: ALTER TABLE tools DROP COLUMN location_id;