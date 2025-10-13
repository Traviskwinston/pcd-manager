-- V33: Update Passdowns to support Many-to-Many relationships with Tools and Users

-- Create join table for Passdown -> Tools (Many-to-Many)
CREATE TABLE IF NOT EXISTS passdown_tools (
    passdown_id BIGINT NOT NULL,
    tool_id BIGINT NOT NULL,
    PRIMARY KEY (passdown_id, tool_id),
    FOREIGN KEY (passdown_id) REFERENCES passdowns(id) ON DELETE CASCADE,
    FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE CASCADE
);

-- Create join table for Passdown -> Users/Technicians (Many-to-Many)
CREATE TABLE IF NOT EXISTS passdown_techs (
    passdown_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (passdown_id, user_id),
    FOREIGN KEY (passdown_id) REFERENCES passdowns(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Migrate existing data from passdowns.tool_id to passdown_tools join table
INSERT INTO passdown_tools (passdown_id, tool_id)
SELECT id, tool_id FROM passdowns WHERE tool_id IS NOT NULL;

-- Migrate existing data from passdowns.assigned_to to passdown_techs join table
INSERT INTO passdown_techs (passdown_id, user_id)
SELECT id, assigned_to FROM passdowns WHERE assigned_to IS NOT NULL;

-- Drop old foreign key columns (they're now in join tables)
ALTER TABLE passdowns DROP COLUMN IF EXISTS tool_id;
ALTER TABLE passdowns DROP COLUMN IF EXISTS assigned_to;

