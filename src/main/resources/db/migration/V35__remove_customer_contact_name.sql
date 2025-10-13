-- Remove customer_contact_name column as it's not needed
ALTER TABLE locations DROP COLUMN IF EXISTS customer_contact_name;

