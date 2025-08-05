-- Diagnostic queries for grid display issue
-- Check current state of locations
SELECT 'LOCATIONS:' as section, '' as details;
SELECT id, name, display_name, state, fab, default_location 
FROM locations 
ORDER BY default_location DESC, id;

-- Check current state of users and their location assignments
SELECT 'USERS:' as section, '' as details;
SELECT id, name, email, active_site_id, default_location_id 
FROM users 
ORDER BY id;

-- Check current state of map grid items
SELECT 'MAP_GRID_ITEMS:' as section, '' as details;
SELECT mgi.id, mgi.type, mgi.x, mgi.y, mgi.width, mgi.height, 
       mgi.tool_id, t.name as tool_name, 
       mgi.location_id, l.display_name as location_name
FROM map_grid_items mgi
LEFT JOIN tools t ON mgi.tool_id = t.id
LEFT JOIN locations l ON mgi.location_id = l.id
ORDER BY mgi.id;

-- Check if tools exist without grid items
SELECT 'TOOLS_WITHOUT_GRID_ITEMS:' as section, '' as details;
SELECT t.id, t.name, t.location_id as tool_location_id, tl.display_name as tool_location_name
FROM tools t
LEFT JOIN locations tl ON t.location_id = tl.id
LEFT JOIN map_grid_items mgi ON mgi.tool_id = t.id
WHERE mgi.id IS NULL
ORDER BY t.name;

-- Count summary
SELECT 'SUMMARY:' as section, '' as details;
SELECT 
  (SELECT COUNT(*) FROM locations) as total_locations,
  (SELECT COUNT(*) FROM locations WHERE default_location = true) as default_locations,
  (SELECT COUNT(*) FROM users) as total_users,
  (SELECT COUNT(*) FROM users WHERE active_site_id IS NOT NULL) as users_with_active_site,
  (SELECT COUNT(*) FROM map_grid_items) as total_grid_items,
  (SELECT COUNT(*) FROM map_grid_items WHERE type = 'TOOL') as tool_grid_items,
  (SELECT COUNT(*) FROM tools) as total_tools; 