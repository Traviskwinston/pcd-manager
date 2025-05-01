-- Alter the passdowns table to support large comments
ALTER TABLE IF EXISTS passdowns ALTER COLUMN comment VARCHAR(10000); 