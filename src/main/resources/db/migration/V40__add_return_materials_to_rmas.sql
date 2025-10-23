-- Add return_materials_to column to rmas table
ALTER TABLE rmas ADD COLUMN IF NOT EXISTS return_materials_to VARCHAR(255);




