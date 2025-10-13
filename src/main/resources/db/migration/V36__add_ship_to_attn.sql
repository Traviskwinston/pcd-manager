-- Add attn field to locations for shipping address
ALTER TABLE locations ADD COLUMN ship_to_attn VARCHAR(255);

