-- Add timezone column to locations table
ALTER TABLE locations ADD COLUMN IF NOT EXISTS time_zone VARCHAR(255);

-- Update existing locations with default timezones based on state
UPDATE locations SET time_zone = 'America/Phoenix' WHERE state = 'AZ' OR state = 'Arizona';
UPDATE locations SET time_zone = 'America/Denver' WHERE state = 'NM' OR state = 'New Mexico';
UPDATE locations SET time_zone = 'America/Chicago' WHERE state = 'TX' OR state = 'Texas';
UPDATE locations SET time_zone = 'America/New_York' WHERE state IN ('NY', 'New York', 'MA', 'Massachusetts', 'NH', 'New Hampshire');
UPDATE locations SET time_zone = 'America/Los_Angeles' WHERE state = 'CA' OR state = 'California';

-- Set default for any remaining null timezones
UPDATE locations SET time_zone = 'America/Phoenix' WHERE time_zone IS NULL;


