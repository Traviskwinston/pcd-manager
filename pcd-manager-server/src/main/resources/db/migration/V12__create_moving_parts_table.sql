-- Create moving parts table
CREATE TABLE moving_parts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    part_name VARCHAR(255) NOT NULL,
    from_tool_id BIGINT,
    to_tool_id BIGINT,
    move_date TIMESTAMP NOT NULL,
    notes VARCHAR(1000),
    note_id BIGINT,
    FOREIGN KEY (from_tool_id) REFERENCES tools(id),
    FOREIGN KEY (to_tool_id) REFERENCES tools(id),
    FOREIGN KEY (note_id) REFERENCES notes(id)
); 