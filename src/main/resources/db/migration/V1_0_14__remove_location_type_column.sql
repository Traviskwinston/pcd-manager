-- Remove location_type column from locations table
-- This column was removed from the Location model as it was determined to be unnecessary
 
ALTER TABLE locations DROP COLUMN IF EXISTS location_type; 