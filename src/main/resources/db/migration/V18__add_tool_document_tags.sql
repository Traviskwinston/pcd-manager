-- Add tool document tags table
-- This table stores document type tags for each document associated with a tool

CREATE TABLE tool_document_tags (
    tool_id BIGINT NOT NULL,
    document_path VARCHAR(500) NOT NULL,
    document_tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (tool_id, document_path),
    FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE CASCADE
);

-- Add index for performance
CREATE INDEX idx_tool_document_tags_tool_id ON tool_document_tags(tool_id);
CREATE INDEX idx_tool_document_tags_tag ON tool_document_tags(document_tag); 