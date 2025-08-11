-- Add GasGuard-specific optional fields to tools
ALTER TABLE tools
    ADD COLUMN IF NOT EXISTS system_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS equipment_location VARCHAR(255),
    ADD COLUMN IF NOT EXISTS config_number VARCHAR(255),
    ADD COLUMN IF NOT EXISTS equipment_set INT;

-- Helpful indexes for filtering/searching
CREATE INDEX IF NOT EXISTS idx_tools_system_name ON tools(system_name);
CREATE INDEX IF NOT EXISTS idx_tools_equipment_location ON tools(equipment_location);

