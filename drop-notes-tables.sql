-- Drop the old notes table since we're switching to tool_comments
DROP TABLE IF EXISTS notes;

-- The new tool_comments table will be created by the migration V4__Add_Tool_Comments_Table.sql 