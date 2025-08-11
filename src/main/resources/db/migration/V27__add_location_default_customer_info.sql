-- Add default customer info fields to locations to auto-fill RMAs when active site is selected
ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS ship_to_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ship_to_address VARCHAR(500),
    ADD COLUMN IF NOT EXISTS ship_to_city VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ship_to_state VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ship_to_zip VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_locations_ship_to_name ON locations(ship_to_name);

