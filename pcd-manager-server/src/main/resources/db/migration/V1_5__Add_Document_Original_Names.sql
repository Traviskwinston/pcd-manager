-- Create the tool_document_names table
CREATE TABLE IF NOT EXISTS tool_document_names (
    tool_id BIGINT NOT NULL,
    original_filename VARCHAR(255),
    document_path VARCHAR(255) NOT NULL,
    PRIMARY KEY (tool_id, document_path),
    FOREIGN KEY (tool_id) REFERENCES tools(id)
);

-- Create the tool_picture_names table
CREATE TABLE IF NOT EXISTS tool_picture_names (
    tool_id BIGINT NOT NULL,
    original_filename VARCHAR(255),
    picture_path VARCHAR(255) NOT NULL,
    PRIMARY KEY (tool_id, picture_path),
    FOREIGN KEY (tool_id) REFERENCES tools(id)
); 