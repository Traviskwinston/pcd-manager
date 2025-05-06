-- Add new fields to the tools table
ALTER TABLE tools
  ADD COLUMN commission_date DATE,
  ADD COLUMN start_up_sl03_date DATE,
  ADD COLUMN chemical_gas_service VARCHAR(255); 