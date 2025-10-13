-- Check NCSR connections to tools
SELECT 
    n.id as ncsr_id,
    n.equipment_number,
    n.tool_id_number,
    n.component,
    n.part_location_id,
    n.tool_id,
    t.name as tool_name,
    t.serial_number1 as tool_serial,
    CASE 
        WHEN n.tool_id IS NULL THEN 'UNLINKED'
        ELSE 'LINKED'
    END as connection_status
FROM ncsrs n
LEFT JOIN tools t ON n.tool_id = t.id
ORDER BY n.id
LIMIT 10;

-- Summary statistics
SELECT 
    COUNT(*) as total_ncsrs,
    COUNT(tool_id) as linked_to_tools,
    COUNT(*) - COUNT(tool_id) as unlinked
FROM ncsrs;


