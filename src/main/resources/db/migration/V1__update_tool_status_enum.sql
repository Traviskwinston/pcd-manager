-- Update the ENUM type for tool status
ALTER TABLE tools ALTER COLUMN status SET NULL;

-- Drop and recreate the constraint for the status column with new enum values
ALTER TABLE tools DROP COLUMN IF EXISTS status;
ALTER TABLE tools ADD COLUMN status VARCHAR(255) NOT NULL DEFAULT 'NOT_STARTED';

-- Update any existing records
UPDATE tools SET status = 'NOT_STARTED' WHERE status = 'AVAILABLE';
UPDATE tools SET status = 'IN_PROGRESS' WHERE status = 'IN_USE' OR status = 'MAINTENANCE';
UPDATE tools SET status = 'COMPLETED' WHERE status = 'DAMAGED' OR status = 'LOST';

-- Update the ENUM type for tool type
ALTER TABLE tools ALTER COLUMN tool_type SET NULL;

-- Drop and recreate the tool_type column to accept CHEMBLEND and SLURRY
ALTER TABLE tools DROP COLUMN IF EXISTS tool_type;
ALTER TABLE tools ADD COLUMN tool_type VARCHAR(255); 