-- Change upload_date column from DATE to TIMESTAMP for millisecond precision
ALTER TABLE tools ALTER COLUMN upload_date TYPE TIMESTAMP;

-- The existing index should still work with TIMESTAMP type
