-- Fix script for grid display issue
-- This script ensures proper location context is set up

-- Step 1: Ensure we have a default location
-- If no default location exists, set Arizona F52 as default
UPDATE locations 
SET default_location = true 
WHERE state = 'Arizona' AND fab = '52' 
  AND NOT EXISTS (SELECT 1 FROM locations WHERE default_location = true);

-- If Arizona F52 doesn't exist, create it as default
INSERT INTO locations (name, display_name, state, fab, default_location)
SELECT 'Arizona Fab 52', 'AZ F52', 'Arizona', '52', true
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE state = 'Arizona' AND fab = '52')
  AND NOT EXISTS (SELECT 1 FROM locations WHERE default_location = true);

-- Step 2: Ensure all users have an active_site set to the default location
UPDATE users 
SET active_site_id = (SELECT id FROM locations WHERE default_location = true LIMIT 1)
WHERE active_site_id IS NULL;

-- Step 3: Ensure all users have a default_location set
UPDATE users 
SET default_location_id = (SELECT id FROM locations WHERE default_location = true LIMIT 1)
WHERE default_location_id IS NULL;

-- Step 4: Ensure all existing map_grid_items have a location_id set
-- This updates any grid items that might not have a location set
UPDATE map_grid_items 
SET location_id = (SELECT id FROM locations WHERE default_location = true LIMIT 1)
WHERE location_id IS NULL;

-- Step 5: For any map_grid_items that have tools, ensure they're associated with the correct location
-- This handles cases where tools might be at different locations than their grid items
UPDATE map_grid_items mgi
SET location_id = COALESCE(
    (SELECT t.location_id FROM tools t WHERE t.id = mgi.tool_id AND t.location_id IS NOT NULL),
    (SELECT id FROM locations WHERE default_location = true LIMIT 1)
)
WHERE mgi.type = 'TOOL' AND mgi.tool_id IS NOT NULL;

-- Display the results
SELECT 'FIXED DATA:' as result;
SELECT 
  (SELECT COUNT(*) FROM locations WHERE default_location = true) as default_locations_count,
  (SELECT COUNT(*) FROM users WHERE active_site_id IS NOT NULL) as users_with_active_site,
  (SELECT COUNT(*) FROM users WHERE default_location_id IS NOT NULL) as users_with_default_location,
  (SELECT COUNT(*) FROM map_grid_items WHERE location_id IS NOT NULL) as grid_items_with_location; 