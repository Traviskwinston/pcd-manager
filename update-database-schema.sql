-- Script to fix the ToolStatus enum issue

-- 1. Create a new temporary column
ALTER TABLE tools ADD COLUMN temp_status VARCHAR(255);

-- 2. Copy and convert values from status to temp_status
UPDATE tools SET temp_status = 'NOT_STARTED' WHERE status = 'AVAILABLE';
UPDATE tools SET temp_status = 'IN_PROGRESS' WHERE status = 'IN_USE' OR status = 'MAINTENANCE';
UPDATE tools SET temp_status = 'COMPLETED' WHERE status = 'DAMAGED' OR status = 'LOST';
UPDATE tools SET temp_status = 'NOT_STARTED' WHERE temp_status IS NULL;

-- 3. Drop the old status column
ALTER TABLE tools DROP COLUMN status;

-- 4. Rename the temporary column to status
ALTER TABLE tools RENAME COLUMN temp_status TO status;

-- 5. Make the status column NOT NULL with a default value
ALTER TABLE tools ALTER COLUMN status SET NOT NULL;
ALTER TABLE tools ALTER COLUMN status SET DEFAULT 'NOT_STARTED';

-- Update tool_type column if needed
UPDATE tools SET tool_type = 'SLURRY' WHERE tool_type NOT IN ('CHEMBLEND', 'SLURRY');

-- Add destination_chain column to moving_parts table for multi-step movements
ALTER TABLE moving_parts ADD COLUMN destination_chain TEXT;

-- Add location_id column to projects table if it doesn't exist
ALTER TABLE projects ADD COLUMN IF NOT EXISTS location_id BIGINT;

-- Add foreign key constraint for projects.location_id if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_projects_location' 
        AND table_name = 'projects'
    ) THEN
        ALTER TABLE projects ADD CONSTRAINT fk_projects_location 
        FOREIGN KEY (location_id) REFERENCES locations(id);
    END IF;
END $$; 