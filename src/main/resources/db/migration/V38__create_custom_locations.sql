-- Create custom_locations table
CREATE TABLE custom_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    location_id BIGINT NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE
);

-- Add custom location reference columns to moving_parts
ALTER TABLE moving_parts
    ADD COLUMN from_custom_location_id BIGINT,
    ADD COLUMN to_custom_location_id BIGINT,
    ADD FOREIGN KEY (from_custom_location_id) REFERENCES custom_locations(id) ON DELETE SET NULL,
    ADD FOREIGN KEY (to_custom_location_id) REFERENCES custom_locations(id) ON DELETE SET NULL;

-- Insert preset custom locations for existing locations
-- We'll use a stored procedure to insert for all locations
-- But for now, let's just insert for the main location (assuming ID 1 is AZ F52)
INSERT INTO custom_locations (name, description, location_id, created_at, updated_at)
SELECT 'Cabinet', 'Storage cabinet for parts and equipment', id, NOW(), NOW()
FROM locations;

INSERT INTO custom_locations (name, description, location_id, created_at, updated_at)
SELECT 'Intel Cage', 'Intel equipment cage storage area', id, NOW(), NOW()
FROM locations;

INSERT INTO custom_locations (name, description, location_id, created_at, updated_at)
SELECT 'Chandler Office', 'Office storage and administrative area', id, NOW(), NOW()
FROM locations;

