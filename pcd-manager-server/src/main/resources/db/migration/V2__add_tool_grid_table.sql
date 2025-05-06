-- Create tool grid positions table
CREATE TABLE IF NOT EXISTS tool_grid_positions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_id BIGINT UNIQUE,
    grid_x INT,
    grid_y INT,
    size_x INT DEFAULT 1,
    size_y INT DEFAULT 1,
    background_color VARCHAR(50),
    status_color VARCHAR(50),
    list_order INT,
    is_placed BOOLEAN DEFAULT FALSE,
    is_locked BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (tool_id) REFERENCES tools(id)
); 