-- Clear all passdowns and related join table data
SET REFERENTIAL_INTEGRITY FALSE;

-- Clear join tables first
TRUNCATE TABLE passdown_tools;
TRUNCATE TABLE passdown_techs;

-- Clear passdown pictures if they exist
DELETE FROM passdown_pictures;

-- Clear passdowns
TRUNCATE TABLE passdowns RESTART IDENTITY;

SET REFERENTIAL_INTEGRITY TRUE;

-- Verify tables are empty
SELECT COUNT(*) as passdown_count FROM passdowns;
SELECT COUNT(*) as passdown_tools_count FROM passdown_tools;
SELECT COUNT(*) as passdown_techs_count FROM passdown_techs;

