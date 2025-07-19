-- Add uploadedBy column to existing rma_pictures table
ALTER TABLE rma_pictures ADD COLUMN uploaded_by_user_id BIGINT;
ALTER TABLE rma_pictures ADD CONSTRAINT FK_rma_pictures_uploaded_by 
    FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id);

-- Create new tool_pictures table with upload tracking
CREATE TABLE tool_pictures (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255),
    file_path TEXT NOT NULL,
    file_type VARCHAR(100),
    file_size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by_user_id BIGINT,
    tool_id BIGINT NOT NULL,
    CONSTRAINT FK_tool_pictures_tool FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE CASCADE,
    CONSTRAINT FK_tool_pictures_uploaded_by FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id)
);

-- Create new passdown_pictures table with upload tracking
CREATE TABLE passdown_pictures (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255),
    file_path TEXT NOT NULL,
    file_type VARCHAR(100),
    file_size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by_user_id BIGINT,
    passdown_id BIGINT NOT NULL,
    CONSTRAINT FK_passdown_pictures_passdown FOREIGN KEY (passdown_id) REFERENCES passdowns(id) ON DELETE CASCADE,
    CONSTRAINT FK_passdown_pictures_uploaded_by FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id)
);

-- Create new tracktrend_pictures table with upload tracking
CREATE TABLE tracktrend_pictures (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255),
    file_path TEXT NOT NULL,
    file_type VARCHAR(100),
    file_size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by_user_id BIGINT,
    tracktrend_id BIGINT NOT NULL,
    CONSTRAINT FK_tracktrend_pictures_tracktrend FOREIGN KEY (tracktrend_id) REFERENCES track_trends(id) ON DELETE CASCADE,
    CONSTRAINT FK_tracktrend_pictures_uploaded_by FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id)
);

-- Rename existing legacy tables to avoid conflicts
ALTER TABLE tool_pictures RENAME TO tool_pictures_legacy;
ALTER TABLE tool_picture_names RENAME TO tool_picture_names_legacy;
ALTER TABLE passdown_pictures RENAME TO passdown_pictures_legacy;
ALTER TABLE passdown_picture_names RENAME TO passdown_picture_names_legacy;
ALTER TABLE tracktrend_picture_paths RENAME TO tracktrend_picture_paths_legacy;
ALTER TABLE tracktrend_picture_names RENAME TO tracktrend_picture_names_legacy;

-- Create indexes for better performance
CREATE INDEX idx_tool_pictures_tool_id ON tool_pictures(tool_id);
CREATE INDEX idx_tool_pictures_uploaded_by ON tool_pictures(uploaded_by_user_id);
CREATE INDEX idx_passdown_pictures_passdown_id ON passdown_pictures(passdown_id);
CREATE INDEX idx_passdown_pictures_uploaded_by ON passdown_pictures(uploaded_by_user_id);
CREATE INDEX idx_tracktrend_pictures_tracktrend_id ON tracktrend_pictures(tracktrend_id);
CREATE INDEX idx_tracktrend_pictures_uploaded_by ON tracktrend_pictures(uploaded_by_user_id);
CREATE INDEX idx_rma_pictures_uploaded_by ON rma_pictures(uploaded_by_user_id); 