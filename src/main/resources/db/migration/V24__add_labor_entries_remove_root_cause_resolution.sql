-- Add labor entries table
CREATE TABLE rma_labor_entries (
    rma_id BIGINT NOT NULL,
    description TEXT,
    technician VARCHAR(255),
    hours DECIMAL(8,2) DEFAULT 0.00,
    labor_date DATE,
    price_per_hour DECIMAL(8,2) DEFAULT 0.00,
    FOREIGN KEY (rma_id) REFERENCES rmas(id) ON DELETE CASCADE
);

-- Remove root_cause and resolution columns from rmas table
ALTER TABLE rmas DROP COLUMN IF EXISTS root_cause;
ALTER TABLE rmas DROP COLUMN IF EXISTS resolution;