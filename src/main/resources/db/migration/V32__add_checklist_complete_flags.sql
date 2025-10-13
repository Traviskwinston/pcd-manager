-- Add boolean completion flags for checklist items (independent of dates)
ALTER TABLE tools ADD COLUMN commission_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN pre_sl1_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN sl1_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN mechanical_pre_sl1_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN mechanical_post_sl1_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN specific_input_functionality_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN modes_of_operation_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN specific_soos_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN field_service_report_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN certificate_of_approval_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN turned_over_to_customer_complete BOOLEAN;
ALTER TABLE tools ADD COLUMN start_up_sl03_complete BOOLEAN;

-- Migrate existing data: set boolean flags based on whether dates exist
UPDATE tools SET commission_complete = CASE WHEN commission_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET pre_sl1_complete = CASE WHEN pre_sl1_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET sl1_complete = CASE WHEN sl1_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET mechanical_pre_sl1_complete = CASE WHEN mechanical_pre_sl1_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET mechanical_post_sl1_complete = CASE WHEN mechanical_post_sl1_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET specific_input_functionality_complete = CASE WHEN specific_input_functionality_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET modes_of_operation_complete = CASE WHEN modes_of_operation_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET specific_soos_complete = CASE WHEN specific_soos_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET field_service_report_complete = CASE WHEN field_service_report_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET certificate_of_approval_complete = CASE WHEN certificate_of_approval_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET turned_over_to_customer_complete = CASE WHEN turned_over_to_customer_date IS NOT NULL THEN TRUE ELSE NULL END;
UPDATE tools SET start_up_sl03_complete = CASE WHEN start_up_sl03_date IS NOT NULL THEN TRUE ELSE NULL END;

