-- Drop any username-related constraints
ALTER TABLE users DROP COLUMN IF EXISTS username;

-- Add new columns
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS active_site_id BIGINT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS active_tool_id BIGINT;

-- Make email NOT NULL
UPDATE users SET email = 'default@example.com' WHERE email IS NULL OR email = '';
ALTER TABLE users ALTER COLUMN email SET NOT NULL;

-- Add foreign key constraints
ALTER TABLE users 
    ADD CONSTRAINT FK_user_active_site
    FOREIGN KEY (active_site_id) 
    REFERENCES locations(id);

ALTER TABLE users 
    ADD CONSTRAINT FK_user_active_tool
    FOREIGN KEY (active_tool_id) 
    REFERENCES tools(id); 