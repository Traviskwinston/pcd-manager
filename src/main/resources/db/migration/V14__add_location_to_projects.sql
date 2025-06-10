-- Add location_id column to projects table
ALTER TABLE projects ADD COLUMN location_id BIGINT;

-- Add foreign key constraint to link to locations table
ALTER TABLE projects ADD CONSTRAINT fk_projects_location_id 
    FOREIGN KEY (location_id) REFERENCES locations(id); 