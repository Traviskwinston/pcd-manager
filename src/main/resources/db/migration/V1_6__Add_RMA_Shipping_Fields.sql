-- Add new shipping information fields to the RMA table
ALTER TABLE rmas
  ADD COLUMN company_ship_to_name VARCHAR(255),
  ADD COLUMN company_ship_to_address VARCHAR(255),
  ADD COLUMN city VARCHAR(100),
  ADD COLUMN state VARCHAR(100),
  ADD COLUMN zip_code VARCHAR(20),
  ADD COLUMN attn VARCHAR(255); 