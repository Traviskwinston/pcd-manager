-- Create tool_type_field_definitions table
CREATE TABLE IF NOT EXISTS tool_type_field_definitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_type VARCHAR(255) NOT NULL,
    field_key VARCHAR(255) NOT NULL,
    field_label VARCHAR(255) NOT NULL,
    field_type VARCHAR(50) NOT NULL,
    dropdown_options_json TEXT,
    is_required BOOLEAN DEFAULT FALSE,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tool_type_field_key (tool_type, field_key)
);

-- Create tool_custom_fields table
CREATE TABLE IF NOT EXISTS tool_custom_fields (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_id BIGINT NOT NULL,
    field_key VARCHAR(255) NOT NULL,
    field_value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tool_field_key (tool_id, field_key),
    FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE CASCADE
);

-- Add indexes for performance
CREATE INDEX idx_field_definitions_tool_type ON tool_type_field_definitions(tool_type);
CREATE INDEX idx_field_definitions_display_order ON tool_type_field_definitions(tool_type, display_order);
CREATE INDEX idx_custom_fields_tool_id ON tool_custom_fields(tool_id);
CREATE INDEX idx_custom_fields_field_key ON tool_custom_fields(field_key);


