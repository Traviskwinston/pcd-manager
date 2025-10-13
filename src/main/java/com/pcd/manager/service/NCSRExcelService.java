package com.pcd.manager.service;

import com.pcd.manager.model.NCSR;
import com.pcd.manager.model.Tool;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class NCSRExcelService {
    
    private static final Logger logger = LoggerFactory.getLogger(NCSRExcelService.class);
    
    @Autowired
    private NCSRService ncsrService;
    
    @Autowired
    private ToolService toolService;
    
    // Known column aliases for flexible column detection
    private static final Map<String, List<String>> COLUMN_ALIASES = new HashMap<>();
    
    static {
        COLUMN_ALIASES.put("versumEmdQuote", Arrays.asList("Versum/EMD Quote", "Versum Quote", "EMD Quote", "Quote"));
        COLUMN_ALIASES.put("customerLocation", Arrays.asList("Customer Location", "Customer", "Cust Location"));
        COLUMN_ALIASES.put("customerPo", Arrays.asList("Customer PO#", "Customer PO", "PO#", "PO Number"));
        COLUMN_ALIASES.put("customerPoReceivedDate", Arrays.asList("Customer PO Received Date", "PO Received Date", "PO Date"));
        COLUMN_ALIASES.put("supplier", Arrays.asList("Supplier", "Vendor"));
        COLUMN_ALIASES.put("supplierPoOrProductionOrder", Arrays.asList("Supplier PO# or Production Order", "Supplier PO", "Production Order", "PO/Production Order"));
        COLUMN_ALIASES.put("finishDate", Arrays.asList("Finish Date", "Completion Date", "Done Date"));
        COLUMN_ALIASES.put("mmNumber", Arrays.asList("MM#", "Model #", "Model Number", "MM", "Model"));
        COLUMN_ALIASES.put("equipmentNumber", Arrays.asList("Equipment #", "Equipment Number", "Equip #", "Equip Number", "Equipment"));
        COLUMN_ALIASES.put("serialNumber", Arrays.asList("Serial #", "Serial Number", "Serial", "S/N"));
        COLUMN_ALIASES.put("description", Arrays.asList("Description", "Desc", "Part Description"));
        COLUMN_ALIASES.put("toolIdNumber", Arrays.asList("Tool ID#", "Tool ID", "ToolID", "Tool Number"));
        COLUMN_ALIASES.put("component", Arrays.asList("Component", "Part Component", "Comp"));
        COLUMN_ALIASES.put("discrepantPartMfg", Arrays.asList("Discrepant Part Mfg", "Part Mfg", "Manufacturer", "Mfg"));
        COLUMN_ALIASES.put("discrepantPartNumber", Arrays.asList("Discrepant Part Number", "Part Number", "Part #", "PN"));
        COLUMN_ALIASES.put("partLocationId", Arrays.asList("Part Location/I.D.", "Part Location", "Location/ID", "Location"));
        COLUMN_ALIASES.put("partQuantity", Arrays.asList("Part Quantity", "Quantity", "Qty", "QTY"));
        COLUMN_ALIASES.put("estShipDate", Arrays.asList("Est Ship Date", "Estimated Ship Date", "Ship Date", "Est. Ship"));
        COLUMN_ALIASES.put("ecrNumber", Arrays.asList("ECR#", "ECR", "ECR Number"));
        COLUMN_ALIASES.put("contractManufacturer", Arrays.asList("Contract Manufacturer", "Contract Mfg", "CM"));
        COLUMN_ALIASES.put("trackingNumberSupplierToFse", Arrays.asList("Tracking # from Supplier to FSE", "Tracking Number", "Tracking #", "Tracking"));
        COLUMN_ALIASES.put("notificationToRobin", Arrays.asList("Notification to Robin (Where shipped)", "Notification to Robin", "Where Shipped", "Robin Notification"));
        COLUMN_ALIASES.put("workInstructionRequired", Arrays.asList("Work Instruction Required?", "WI Required", "Work Instruction", "WI Required?"));
        COLUMN_ALIASES.put("workInstructionIdentifier", Arrays.asList("Work Instruction Identifier", "WI Identifier", "WI ID", "Work Instruction ID"));
        COLUMN_ALIASES.put("fseFieldServiceCompletionDate", Arrays.asList("FSE Field Service Completion Date", "FSE Completion Date", "Service Completion Date", "Completion Date"));
        COLUMN_ALIASES.put("toolOwner", Arrays.asList("Tool Owner", "Owner"));
        COLUMN_ALIASES.put("status", Arrays.asList("Open\\nClosed", "Open/Closed", "Status", "Open Closed"));
        COLUMN_ALIASES.put("comments", Arrays.asList("Comments", "Notes", "Comment"));
    }
    
    /**
     * Parse Excel file and return import preview data
     */
    public Map<String, Object> parseExcelForPreview(MultipartFile file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> rowData = new ArrayList<>();
        Map<String, String> detectedColumns = new HashMap<>();
        List<String> undetectedColumns = new ArrayList<>();
        List<String> headerRow = new ArrayList<>();
        int totalRows = 0;
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            // Log workbook info
            logger.info("Excel Preview - Workbook has {} sheets", workbook.getNumberOfSheets());
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet s = workbook.getSheetAt(i);
                logger.info("  Sheet {}: '{}', Physical rows: {}, Last row num: {}", 
                    i, s.getSheetName(), s.getPhysicalNumberOfRows(), s.getLastRowNum());
            }
            
            Sheet sheet = workbook.getSheetAt(0);
            
            // Log sheet info
            logger.info("Excel Preview - Using sheet: '{}', Physical rows: {}, Last row num: {}", 
                sheet.getSheetName(), sheet.getPhysicalNumberOfRows(), sheet.getLastRowNum());
            
            Iterator<Row> rowIterator = sheet.iterator();
            
            if (!rowIterator.hasNext()) {
                throw new IllegalArgumentException("Excel file is empty");
            }
            
            // Read header row
            Row firstRow = rowIterator.next();
            for (Cell cell : firstRow) {
                String header = getCellValueAsString(cell).trim();
                headerRow.add(header);
            }
            
            logger.info("Excel Preview - Header columns found: {}", headerRow.size());
            
            // Detect columns
            for (int i = 0; i < headerRow.size(); i++) {
                String header = headerRow.get(i);
                String fieldName = detectFieldName(header);
                if (fieldName != null) {
                    detectedColumns.put(String.valueOf(i), fieldName);
                } else {
                    undetectedColumns.add(header);
                }
            }
            
            // Read data rows and count total non-empty rows
            int rowCount = 0;
            int skippedEmptyRows = 0;
            int actualRowNum = 1; // Start at 1 (after header at 0)
            
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                actualRowNum = row.getRowNum(); // Get actual Excel row number
                
                // Check if row is empty (skip completely blank rows)
                boolean isEmptyRow = true;
                for (int i = 0; i < headerRow.size(); i++) {
                    Cell cell = row.getCell(i);
                    String value = cell != null ? getCellValueAsString(cell) : "";
                    if (value != null && !value.trim().isEmpty()) {
                        isEmptyRow = false;
                        break;
                    }
                }
                
                if (!isEmptyRow) {
                    totalRows++; // Count non-empty rows
                    
                    // Only add to preview if under limit
                    if (rowCount < 100) {
                        Map<String, Object> rowMap = new HashMap<>();
                        for (int i = 0; i < headerRow.size(); i++) {
                            Cell cell = row.getCell(i);
                            String value = cell != null ? getCellValueAsString(cell) : "";
                            rowMap.put(String.valueOf(i), value);
                        }
                        rowData.add(rowMap);
                        rowCount++;
                    }
                } else {
                    skippedEmptyRows++;
                }
            }
            
            logger.info("Excel Preview - Total non-empty rows: {}, Skipped empty rows: {}, Last Excel row num: {}", 
                totalRows, skippedEmptyRows, actualRowNum);
        }
        
        result.put("headerRow", headerRow);
        result.put("detectedColumns", detectedColumns);
        result.put("undetectedColumns", undetectedColumns);
        result.put("rowData", rowData);
        result.put("sampleRow", rowData.isEmpty() ? new HashMap<>() : rowData.get(0));
        result.put("totalRows", totalRows);
        
        return result;
    }
    
    /**
     * Import NCSR records from Excel with column mapping
     */
    public Map<String, Object> importFromExcel(MultipartFile file, Map<String, String> columnMapping) throws IOException {
        List<NCSR> createdNcsrs = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", 0);
        stats.put("created", 0);
        stats.put("ignored", 0);
        stats.put("warnings", 0);
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Log sheet info
            logger.info("Excel Import - Sheet name: '{}', Physical rows: {}, Last row num: {}", 
                sheet.getSheetName(), sheet.getPhysicalNumberOfRows(), sheet.getLastRowNum());
            
            Iterator<Row> rowIterator = sheet.iterator();
            
            if (!rowIterator.hasNext()) {
                throw new IllegalArgumentException("Excel file is empty");
            }
            
            // Skip header row
            Row headerRow = rowIterator.next();
            logger.info("Excel Import - Header row has {} cells", headerRow.getPhysicalNumberOfCells());
            
            int rowNum = 1;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowNum++;
                
                // Check if row is completely empty - skip if so
                boolean isEmptyRow = true;
                for (Cell cell : row) {
                    String value = getCellValueAsString(cell);
                    if (value != null && !value.trim().isEmpty()) {
                        isEmptyRow = false;
                        break;
                    }
                }
                
                if (isEmptyRow) {
                    continue; // Skip empty rows
                }
                
                stats.put("total", stats.get("total") + 1);
                
                try {
                    NCSR ncsr = parseRowToNCSR(row, columnMapping);
                    
                    // Determine what to use for matching: Equipment# or Tool ID#
                    String matchingValue = null;
                    String matchingSource = null;
                    
                    if (ncsr.getEquipmentNumber() != null && !ncsr.getEquipmentNumber().trim().isEmpty()) {
                        matchingValue = ncsr.getEquipmentNumber();
                        matchingSource = "Equipment#";
                    } else if (ncsr.getToolIdNumber() != null && !ncsr.getToolIdNumber().trim().isEmpty()) {
                        // Extract tool name from Tool ID# (e.g., "BT151" from "BT151(2310216)")
                        matchingValue = extractToolNameFromToolId(ncsr.getToolIdNumber());
                        matchingSource = "Tool ID#";
                        logger.info("Row {}: Extracted '{}' from Tool ID# '{}'", rowNum, matchingValue, ncsr.getToolIdNumber());
                    }
                    
                    // Try to find matching tool
                    if (matchingValue == null || matchingValue.trim().isEmpty()) {
                        logger.warn("Row {}: No Equipment# or Tool ID# found", rowNum);
                        Map<String, Object> warning = new HashMap<>();
                        warning.put("row", rowNum);
                        warning.put("type", "NO_EQUIPMENT");
                        warning.put("message", "No Equipment# or Tool ID# found. Manual tool assignment required.");
                        warning.put("ncsr", ncsr);
                        warnings.add(warning);
                        stats.put("warnings", stats.get("warnings") + 1);
                    } else {
                        logger.info("Row {}: Processing {} '{}'", rowNum, matchingSource, matchingValue);
                        
                        // Try to find matching tool
                        List<Tool> matchingTools = ncsrService.findPotentialToolMatches(matchingValue);
                        
                        if (!matchingTools.isEmpty()) {
                            // Auto-assign to first match
                            ncsr.setTool(matchingTools.get(0));
                            logger.info("✓ Row {}: Auto-assigned NCSR ({}: '{}') to Tool: '{}' (ID: {})", 
                                rowNum, matchingSource, matchingValue, matchingTools.get(0).getName(), matchingTools.get(0).getId());
                            
                            // Add to matched list
                            createdNcsrs.add(ncsr);
                            stats.put("created", stats.get("created") + 1);
                        } else {
                            logger.warn("✗ Row {}: No matching tool found for {}: '{}'", rowNum, matchingSource, matchingValue);
                            
                            // No match found - add to warnings for potential tool creation (NOT to createdNcsrs)
                            Map<String, Object> warning = new HashMap<>();
                            warning.put("row", rowNum);
                            warning.put("type", "NO_MATCH");
                            warning.put("message", "No matching tool found for " + matchingSource + ": " + matchingValue);
                            warning.put("ncsr", ncsr);
                            warning.put("canCreateTool", true);
                            warnings.add(warning);
                            stats.put("warnings", stats.get("warnings") + 1);
                        }
                    }
                    
                } catch (Exception e) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("row", rowNum);
                    error.put("message", "Error parsing row: " + e.getMessage());
                    errors.add(error);
                    logger.error("Error parsing NCSR row {}: {}", rowNum, e.getMessage());
                }
            }
            
            logger.info("Excel Import - Processed {} total rows, {} matched, {} warnings, {} errors", 
                stats.get("total"), stats.get("created"), stats.get("warnings"), errors.size());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("ncsrs", createdNcsrs);
        result.put("warnings", warnings);
        result.put("errors", errors);
        result.put("stats", stats);
        
        return result;
    }
    
    /**
     * Create tool from NCSR data
     */
    public Tool createToolFromNCSR(NCSR ncsr) {
        Tool tool = new Tool();
        tool.setName(ncsr.getToolIdNumber() != null ? ncsr.getToolIdNumber() : "Unknown");
        tool.setSerialNumber1(ncsr.getEquipmentNumber());
        tool.setToolType(Tool.ToolType.UNKNOWN); // Default type
        tool.setLocationName("Unknown"); // Will need to be set manually
        
        return toolService.saveTool(tool);
    }
    
    /**
     * Extract tool name from Tool ID# field
     * Pattern: 2-4 letters followed by 3 numbers (e.g., "BT151" from "BT151(2310216)")
     */
    private String extractToolNameFromToolId(String toolId) {
        if (toolId == null || toolId.trim().isEmpty()) {
            return null;
        }
        
        // Pattern: 2-4 letters followed by 3 numbers
        // Examples: BT151, DSS100, ECO200, ABCD123
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^([A-Z]{2,4}\\d{3})");
        java.util.regex.Matcher matcher = pattern.matcher(toolId.trim().toUpperCase());
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Fallback: just take everything before the first "(" if present
        int parenIndex = toolId.indexOf('(');
        if (parenIndex > 0) {
            return toolId.substring(0, parenIndex).trim();
        }
        
        return toolId.trim();
    }
    
    /**
     * Parse Excel row to NCSR object
     */
    private NCSR parseRowToNCSR(Row row, Map<String, String> columnMapping) {
        NCSR ncsr = new NCSR();
        
        for (Map.Entry<String, String> entry : columnMapping.entrySet()) {
            int colIndex = Integer.parseInt(entry.getKey());
            String fieldName = entry.getValue();
            Cell cell = row.getCell(colIndex);
            String value = cell != null ? getCellValueAsString(cell) : "";
            
            setFieldValue(ncsr, fieldName, value, cell);
        }
        
        // Set defaults
        if (ncsr.getStatus() == null) {
            ncsr.setStatus(NCSR.NcsrStatus.OPEN);
        }
        if (ncsr.getInstalled() == null) {
            ncsr.setInstalled(false);
        }
        
        return ncsr;
    }
    
    /**
     * Set field value on NCSR object
     */
    private void setFieldValue(NCSR ncsr, String fieldName, String value, Cell cell) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        
        try {
            switch (fieldName) {
                case "versumEmdQuote":
                    ncsr.setVersumEmdQuote(value);
                    break;
                case "customerLocation":
                    ncsr.setCustomerLocation(value);
                    break;
                case "customerPo":
                    ncsr.setCustomerPo(value);
                    break;
                case "customerPoReceivedDate":
                    ncsr.setCustomerPoReceivedDate(parseDateValue(cell, value));
                    break;
                case "supplier":
                    ncsr.setSupplier(value);
                    break;
                case "supplierPoOrProductionOrder":
                    ncsr.setSupplierPoOrProductionOrder(value);
                    break;
                case "finishDate":
                    ncsr.setFinishDate(parseDateValue(cell, value));
                    break;
                case "mmNumber":
                    ncsr.setMmNumber(value);
                    break;
                case "equipmentNumber":
                    ncsr.setEquipmentNumber(value);
                    break;
                case "serialNumber":
                    ncsr.setSerialNumber(value);
                    break;
                case "description":
                    ncsr.setDescription(value);
                    break;
                case "toolIdNumber":
                    ncsr.setToolIdNumber(value);
                    break;
                case "component":
                    ncsr.setComponent(value);
                    break;
                case "discrepantPartMfg":
                    ncsr.setDiscrepantPartMfg(value);
                    break;
                case "discrepantPartNumber":
                    ncsr.setDiscrepantPartNumber(value);
                    break;
                case "partLocationId":
                    ncsr.setPartLocationId(value);
                    break;
                case "partQuantity":
                    try {
                        ncsr.setPartQuantity(Integer.parseInt(value.replaceAll("[^0-9]", "")));
                    } catch (NumberFormatException e) {
                        logger.warn("Could not parse quantity: {}", value);
                    }
                    break;
                case "estShipDate":
                    ncsr.setEstShipDate(parseDateValue(cell, value));
                    break;
                case "ecrNumber":
                    ncsr.setEcrNumber(value);
                    break;
                case "contractManufacturer":
                    ncsr.setContractManufacturer(value);
                    break;
                case "trackingNumberSupplierToFse":
                    ncsr.setTrackingNumberSupplierToFse(value);
                    break;
                case "notificationToRobin":
                    ncsr.setNotificationToRobin(value);
                    break;
                case "workInstructionRequired":
                    ncsr.setWorkInstructionRequired(parseBooleanValue(value));
                    break;
                case "workInstructionIdentifier":
                    ncsr.setWorkInstructionIdentifier(value);
                    break;
                case "fseFieldServiceCompletionDate":
                    ncsr.setFseFieldServiceCompletionDate(parseDateValue(cell, value));
                    break;
                case "toolOwner":
                    ncsr.setToolOwner(value);
                    break;
                case "status":
                    String statusUpper = value.toUpperCase().trim();
                    if (statusUpper.contains("OPEN")) {
                        ncsr.setStatus(NCSR.NcsrStatus.OPEN);
                        ncsr.setInstalled(false);
                    } else if (statusUpper.contains("CLOSED") || statusUpper.contains("CLOSE")) {
                        ncsr.setStatus(NCSR.NcsrStatus.CLOSED);
                        ncsr.setInstalled(true);
                    }
                    break;
                case "comments":
                    ncsr.setComments(value);
                    break;
            }
        } catch (Exception e) {
            logger.warn("Error setting field {} to value {}: {}", fieldName, value, e.getMessage());
        }
    }
    
    /**
     * Detect field name from column header
     */
    private String detectFieldName(String header) {
        String normalizedHeader = header.trim().toLowerCase();
        
        for (Map.Entry<String, List<String>> entry : COLUMN_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (alias.toLowerCase().equals(normalizedHeader) || 
                    alias.toLowerCase().replace("/", "").replace("#", "").equals(normalizedHeader.replace("/", "").replace("#", ""))) {
                    return entry.getKey();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    /**
     * Parse date value from cell
     */
    private LocalDate parseDateValue(Cell cell, String value) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        
        // Try parsing string date formats
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            logger.warn("Could not parse date: {}", value);
            return null;
        }
    }
    
    /**
     * Parse boolean value from string
     */
    private Boolean parseBooleanValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        String normalized = value.trim().toLowerCase();
        return normalized.equals("yes") || normalized.equals("true") || normalized.equals("y") || normalized.equals("1");
    }
}

