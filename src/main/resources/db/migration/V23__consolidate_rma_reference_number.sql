-- Consolidate RMA number and SAP notification number into a single reference_number field
-- Add the new reference_number column
ALTER TABLE rmas ADD COLUMN reference_number VARCHAR(255);

-- Migrate existing data: prioritize rma_number, fall back to sap_notification_number
UPDATE rmas SET reference_number = COALESCE(
    NULLIF(TRIM(rma_number), ''),
    NULLIF(TRIM(sap_notification_number), '')
);

-- Create index for the new reference_number column
CREATE INDEX idx_rmas_reference_number ON rmas(reference_number);

-- Add comments to document the change
COMMENT ON COLUMN rmas.reference_number IS 'Consolidated field that can contain either RMA number or SAP notification number';

-- Drop the old columns (commented out for safety - uncomment after verifying migration)
-- ALTER TABLE rmas DROP COLUMN rma_number;
-- ALTER TABLE rmas DROP COLUMN sap_notification_number;