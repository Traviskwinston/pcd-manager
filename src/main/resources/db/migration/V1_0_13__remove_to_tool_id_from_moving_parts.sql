-- Remove to_tool_id column from moving_parts table
-- This column is no longer needed as we're using destination_chain for movement tracking

-- First, let's check if the column exists and remove the foreign key constraint if it exists
DO $$
BEGIN
    -- Remove foreign key constraint if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name LIKE '%moving_parts%to_tool%' 
        AND table_name = 'moving_parts'
    ) THEN
        ALTER TABLE moving_parts DROP CONSTRAINT IF EXISTS fk_moving_parts_to_tool;
    END IF;
    
    -- Remove the column if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'moving_parts' 
        AND column_name = 'to_tool_id'
    ) THEN
        ALTER TABLE moving_parts DROP COLUMN to_tool_id;
    END IF;
END $$; 