-- Add customer information fields to locations table
ALTER TABLE locations ADD COLUMN customer_name VARCHAR(255);
ALTER TABLE locations ADD COLUMN customer_contact_name VARCHAR(255);
ALTER TABLE locations ADD COLUMN customer_phone VARCHAR(50);
ALTER TABLE locations ADD COLUMN customer_email VARCHAR(255);

