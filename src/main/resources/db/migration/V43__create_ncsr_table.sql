-- Create NCSR table
CREATE TABLE IF NOT EXISTS ncsrs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_id BIGINT,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    installed BOOLEAN NOT NULL DEFAULT FALSE,
    install_date DATE,
    
    -- Quote and Customer Info
    versum_emd_quote VARCHAR(500),
    customer_location VARCHAR(500),
    customer_po VARCHAR(255),
    customer_po_received_date DATE,
    
    -- Supplier Info
    supplier VARCHAR(500),
    supplier_po_or_production_order VARCHAR(500),
    finish_date DATE,
    
    -- Equipment/Part Info
    mm_number VARCHAR(255),
    equipment_number VARCHAR(255),
    serial_number VARCHAR(255),
    description VARCHAR(1000),
    tool_id_number VARCHAR(255),
    component VARCHAR(500),
    discrepant_part_mfg VARCHAR(500),
    discrepant_part_number VARCHAR(255),
    part_location_id VARCHAR(500),
    part_quantity INT,
    
    -- Shipping Info
    est_ship_date DATE,
    ecr_number VARCHAR(255),
    contract_manufacturer VARCHAR(500),
    tracking_number_supplier_to_fse VARCHAR(500),
    notification_to_robin VARCHAR(1000),
    
    -- Work Instructions
    work_instruction_required BOOLEAN DEFAULT FALSE,
    work_instruction_identifier VARCHAR(500),
    
    -- Completion Info
    fse_field_service_completion_date DATE,
    tool_owner VARCHAR(500),
    comments TEXT,
    
    FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE SET NULL
);

-- Add indexes for performance
CREATE INDEX idx_ncsr_tool_id ON ncsrs(tool_id);
CREATE INDEX idx_ncsr_equipment_number ON ncsrs(equipment_number);
CREATE INDEX idx_ncsr_status ON ncsrs(status);
CREATE INDEX idx_ncsr_installed ON ncsrs(installed);


