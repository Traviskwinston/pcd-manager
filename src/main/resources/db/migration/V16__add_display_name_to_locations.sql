-- Add display_name column to locations table
ALTER TABLE locations ADD COLUMN display_name VARCHAR(255);

-- Update existing locations with computed display names
UPDATE locations 
SET display_name = CASE 
    WHEN state = 'Arizona' AND fab = '52' THEN 'AZ F52'
    WHEN state = 'New Mexico' AND fab = '25' THEN 'NM F25'
    WHEN state = 'Ireland' AND fab = '10' THEN 'IE F10'
    ELSE UPPER(SUBSTRING(COALESCE(state, '') FROM 1 FOR 2)) || ' F' || COALESCE(fab, '')
END
WHERE state IS NOT NULL AND fab IS NOT NULL;

-- Set display_name for any locations without state/fab
UPDATE locations 
SET display_name = 'Location'
WHERE display_name IS NULL; 