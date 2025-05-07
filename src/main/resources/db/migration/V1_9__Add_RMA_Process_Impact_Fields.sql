-- Add process impact fields to the RMA table
ALTER TABLE rmas
  ADD COLUMN interruption_to_flow BOOLEAN DEFAULT FALSE,
  ADD COLUMN interruption_to_production BOOLEAN DEFAULT FALSE,
  ADD COLUMN downtime_hours DOUBLE PRECISION DEFAULT 0.0,
  ADD COLUMN exposed_to_process_gas_or_chemicals BOOLEAN DEFAULT FALSE,
  ADD COLUMN purged BOOLEAN DEFAULT FALSE,
  ADD COLUMN instructions_for_exposed_component TEXT; 