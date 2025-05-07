-- Add the new date fields
ALTER TABLE tools 
  ADD COLUMN pre_sl1_date DATE,
  ADD COLUMN sl1_date DATE,
  ADD COLUMN sl2_date DATE,
  ADD COLUMN electrical_operation_pre_sl1_date DATE,
  ADD COLUMN hazardous_energy_checklist_date DATE,
  ADD COLUMN mechanical_pre_sl1_date DATE,
  ADD COLUMN mechanical_post_sl1_date DATE,
  ADD COLUMN specific_input_functionality_date DATE,
  ADD COLUMN modes_of_operation_date DATE,
  ADD COLUMN specific_soos_date DATE,
  ADD COLUMN field_service_report_date DATE,
  ADD COLUMN certificate_of_approval_date DATE,
  ADD COLUMN turned_over_to_customer_date DATE;

-- Migrate existing data - set appropriate date where checkbox was true
UPDATE tools 
  SET pre_sl1_date = CURRENT_DATE 
  WHERE pre_sl1completed = TRUE;

UPDATE tools 
  SET sl1_date = CURRENT_DATE 
  WHERE sl1completed = TRUE;

UPDATE tools 
  SET sl2_date = CURRENT_DATE 
  WHERE sl2completed = TRUE;

UPDATE tools 
  SET electrical_operation_pre_sl1_date = CURRENT_DATE 
  WHERE electrical_operation_pre_sl1completed = TRUE;

UPDATE tools 
  SET hazardous_energy_checklist_date = CURRENT_DATE 
  WHERE hazardous_energy_checklist_completed = TRUE;

UPDATE tools 
  SET mechanical_pre_sl1_date = CURRENT_DATE 
  WHERE mechanical_pre_sl1completed = TRUE;

UPDATE tools 
  SET mechanical_post_sl1_date = CURRENT_DATE 
  WHERE mechanical_post_sl1completed = TRUE;

UPDATE tools 
  SET specific_input_functionality_date = CURRENT_DATE 
  WHERE specific_input_functionality_tested = TRUE;

UPDATE tools 
  SET modes_of_operation_date = CURRENT_DATE 
  WHERE modes_of_operation_tested = TRUE;

UPDATE tools 
  SET specific_soos_date = CURRENT_DATE 
  WHERE specific_soos_testsed = TRUE;

UPDATE tools 
  SET field_service_report_date = CURRENT_DATE 
  WHERE field_service_report_uploaded = TRUE;

UPDATE tools 
  SET certificate_of_approval_date = CURRENT_DATE 
  WHERE certificate_of_approval_uploaded = TRUE;

UPDATE tools 
  SET turned_over_to_customer_date = CURRENT_DATE 
  WHERE turned_over_to_customer = TRUE;

-- Drop the boolean columns (optional, can be done later if migration is successful)
ALTER TABLE tools
  DROP COLUMN pre_sl1completed,
  DROP COLUMN sl1completed,
  DROP COLUMN sl2completed,
  DROP COLUMN electrical_operation_pre_sl1completed,
  DROP COLUMN hazardous_energy_checklist_completed,
  DROP COLUMN mechanical_pre_sl1completed,
  DROP COLUMN mechanical_post_sl1completed,
  DROP COLUMN specific_input_functionality_tested,
  DROP COLUMN modes_of_operation_tested,
  DROP COLUMN specific_soos_testsed,
  DROP COLUMN field_service_report_uploaded,
  DROP COLUMN certificate_of_approval_uploaded,
  DROP COLUMN turned_over_to_customer; 