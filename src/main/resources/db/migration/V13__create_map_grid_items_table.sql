-- Create table for map grid items (facility map)
CREATE TABLE map_grid_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    text VARCHAR(255),
    color VARCHAR(20),
    is_solid BOOLEAN,
    tool_id BIGINT,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT fk_map_grid_tool FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE CASCADE,
    CONSTRAINT fk_map_grid_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_map_grid_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
); 