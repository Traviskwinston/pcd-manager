-- Add startup SO3 complete and failed on install fields to RMA table
ALTER TABLE rmas ADD COLUMN startup_so3_complete BOOLEAN DEFAULT FALSE;
ALTER TABLE rmas ADD COLUMN failed_on_install BOOLEAN DEFAULT FALSE; 