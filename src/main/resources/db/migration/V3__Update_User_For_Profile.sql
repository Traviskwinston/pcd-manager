-- Add new columns for first_name and last_name
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS first_name VARCHAR(255);

ALTER TABLE users 
ADD COLUMN IF NOT EXISTS last_name VARCHAR(255);

-- If name column exists, initialize first_name and last_name from it
UPDATE users 
SET first_name = SUBSTRING_INDEX(name, ' ', 1),
    last_name = SUBSTRING(name, LENGTH(SUBSTRING_INDEX(name, ' ', 1)) + 2)
WHERE name IS NOT NULL AND name != '';

-- Add roles column based on role column
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS roles VARCHAR(255);

-- Initialize roles from role
UPDATE users
SET roles = role
WHERE role IS NOT NULL AND role != '';

-- Add default_location_id based on active_site_id
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS default_location_id BIGINT;

-- Initialize default_location_id from active_site_id
UPDATE users
SET default_location_id = active_site_id
WHERE active_site_id IS NOT NULL;

-- Create user_tool_assignments table if it doesn't exist
CREATE TABLE IF NOT EXISTS user_tool_assignments (
    user_id BIGINT NOT NULL,
    tool_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, tool_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE CASCADE
);

-- Add index on tool_id
CREATE INDEX IF NOT EXISTS idx_user_tool_assignments_tool_id ON user_tool_assignments(tool_id); 