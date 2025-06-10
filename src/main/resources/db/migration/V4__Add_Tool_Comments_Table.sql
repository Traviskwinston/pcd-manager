-- Add tool_comments table
CREATE TABLE tool_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content VARCHAR(2000) NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tool_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Add index for better query performance
CREATE INDEX idx_tool_comments_tool_id ON tool_comments(tool_id);
CREATE INDEX idx_tool_comments_created_date ON tool_comments(created_date); 