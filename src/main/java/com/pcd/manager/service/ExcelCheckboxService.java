package com.pcd.manager.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to manipulate Excel checkboxes by finding their linked cells
 * and setting those cells to TRUE
 */
@Service
@Slf4j
public class ExcelCheckboxService {

    public void checkAllCheckboxes() {
        String filePath = "uploads/reference-documents/BlankRMA.xlsx";
        String outputPath = "uploads/reference-documents/BlankRMA_CHECKED.xlsx";
        
        try (FileInputStream fis = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            
            log.info("========================================");
            log.info("TESTING: Finding the cell for Purged = NO");
            log.info("File: BlankRMA.xlsx");
            log.info("Setting ALL boolean cells to TRUE");
            log.info("========================================\n");
            
            java.util.List<String> modifiedCells = new java.util.ArrayList<>();
            
            // Scan ALL sheets for ALL boolean cells (including A61-A100 this time)
            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                XSSFSheet sheet = workbook.getSheetAt(sheetIdx);
                String sheetName = sheet.getSheetName();
                
                log.info("Scanning sheet: {}", sheetName);
                
                for (Row row : sheet) {
                    if (row == null) continue;
                    
                    for (Cell cell : row) {
                        if (cell == null) continue;
                        
                        int rowIdx = cell.getRowIndex();
                        int colIdx = cell.getColumnIndex();
                        String cellRef = getColumnLetter(colIdx) + (rowIdx + 1);
                        
                        // Check if it's a boolean cell
                        if (cell.getCellType() == CellType.BOOLEAN) {
                            boolean currentValue = cell.getBooleanCellValue();
                            log.info("  Found BOOLEAN cell at {} (Sheet: {}): current={} -> Setting to TRUE", 
                                cellRef, sheetName, currentValue);
                            cell.setCellValue(true);
                            modifiedCells.add(cellRef + " (" + sheetName + ", was: " + currentValue + ")");
                        }
                    }
                }
            }
            
            log.info("\n========================================");
            log.info("COMPLETE LIST - Modified {} boolean cells:", modifiedCells.size());
            log.info("========================================");
            for (String cellInfo : modifiedCells) {
                log.info("  ✓ {}", cellInfo);
            }
            log.info("========================================\n");
            
            // Save the modified workbook
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
                log.info("✓ Saved to: {}", outputPath);
                log.info("\nNOW: Open the file and tell me which checkboxes are checked!");
                log.info("We need to find which cell controls 'Purged = NO'");
            }
            
        } catch (Exception e) {
            log.error("Error processing Excel file", e);
            throw new RuntimeException("Failed to check checkboxes: " + e.getMessage(), e);
        }
    }
    
    private void setCellBoolean(XSSFSheet sheet, int rowIndex, int colIndex, boolean value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            cell = row.createCell(colIndex);
        }
        cell.setCellValue(value);
    }
    
    /**
     * Try to find the linked cell for a checkbox using various methods
     */
    private String findLinkedCell(XSSFShape shape, XSSFSheet sheet) {
        try {
            // Try method 1: getControlFormat().getLinkedCell()
            try {
                java.lang.reflect.Method getControlFormatMethod = shape.getClass().getMethod("getControlFormat");
                Object controlFormat = getControlFormatMethod.invoke(shape);
                
                if (controlFormat != null) {
                    java.lang.reflect.Method getLinkedCellMethod = controlFormat.getClass().getMethod("getLinkedCell");
                    String linkedCell = (String) getLinkedCellMethod.invoke(controlFormat);
                    if (linkedCell != null && !linkedCell.isEmpty()) {
                        return linkedCell;
                    }
                }
            } catch (Exception e) {
                // Method not available, try next approach
            }
            
            // Try method 2: getCTShape and look for fmla attribute
            try {
                java.lang.reflect.Method getCTShapeMethod = shape.getClass().getMethod("getCTShape");
                Object ctShape = getCTShapeMethod.invoke(shape);
                
                if (ctShape != null) {
                    // Try to get the client data which contains the cell link
                    String xml = ctShape.toString();
                    if (xml.contains("fmla")) {
                        // Parse out the cell reference
                        log.debug("    CTShape XML contains fmla: {}", xml);
                    }
                }
            } catch (Exception e) {
                // Method not available
            }
            
        } catch (Exception e) {
            log.debug("    Error finding linked cell: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Analyzes an Excel file to find all checkboxes and their states
     * @param filePath Path to the Excel file
     * @return Map containing checkbox analysis results
     */
    public Map<String, Object> analyzeCheckboxes(String filePath) {
        Map<String, Object> result = new HashMap<>();
        java.util.List<Map<String, Object>> checkboxes = new java.util.ArrayList<>();
        java.util.List<String> zipAnalysisLog = new java.util.ArrayList<>();
        int totalCheckboxes = 0;
        int checkedCheckboxes = 0;
        
        log.info("========================================");
        log.info("ANALYZING CHECKBOXES IN: {}", filePath);
        log.info("========================================");
        
        // SAFETY: Create a temporary copy to ensure we NEVER modify the original file
        String tempFilePath = null;
        try {
            java.io.File originalFile = new java.io.File(filePath);
            java.io.File tempFile = java.io.File.createTempFile("checkbox_analysis_", ".xlsx");
            tempFilePath = tempFile.getAbsolutePath();
            
            // Copy original to temp
            java.nio.file.Files.copy(originalFile.toPath(), tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Created temporary copy for analysis: {}", tempFilePath);
            log.info("Original file will NOT be modified: {}", filePath);
            
            // Use the temp file for all analysis
            filePath = tempFilePath;
        } catch (Exception e) {
            log.warn("Could not create temp file, will analyze original (read-only): {}", e.getMessage());
        }
        
        // First, read the ctrlProps from the ZIP to get ACTUAL checkbox states
        log.info("Reading checkbox states from ZIP file structure...");
        Map<String, Boolean> ctrlPropsStates = new HashMap<>();
        try {
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(filePath))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                
                // Look for ctrlProps files which contain the TRUE checkbox state
                if (entryName.contains("xl/ctrlProps/")) {
                    byte[] content = zis.readAllBytes();
                    String xmlContent = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                    
                    // Extract the ctrlProps file number (e.g., "ctrlProps2.xml" -> "2")
                    String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    
                    // Check if this checkbox is checked by looking for checked="Checked" attribute
                    boolean isChecked = xmlContent.contains("checked=\"Checked\"");
                    
                    ctrlPropsStates.put(fileName, isChecked);
                    log.debug("Found {}: isChecked={}", fileName, isChecked);
                }
                    zis.closeEntry();
                }
            } catch (Exception e) {
                log.error("Error reading ctrlProps from ZIP: {}", e.getMessage(), e);
            }
            log.info("Found {} ctrlProps files with checkbox states", ctrlPropsStates.size());
            
            log.info("========================================");
            
            try (FileInputStream fis = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            
            // First, scan all sheets for actual checkbox form controls (the shapes)
            log.info("\nSearching for checkbox form controls...");
            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                XSSFSheet sheet = workbook.getSheetAt(sheetIdx);
                String sheetName = sheet.getSheetName();
                
                XSSFDrawing drawing = sheet.getDrawingPatriarch();
                if (drawing != null) {
                    log.info("  Sheet '{}' has drawing objects", sheetName);
                    java.util.List<XSSFShape> shapes = drawing.getShapes();
                    log.info("  Found {} shapes on sheet '{}'", shapes.size(), sheetName);
                    
                    for (XSSFShape shape : shapes) {
                        String shapeName = shape.getShapeName();
                        if (shapeName != null && (shapeName.toLowerCase().contains("check") || 
                                                   shapeName.toLowerCase().contains("box"))) {
                            
                            // Try to get the checkbox state using reflection
                            boolean isChecked = false;
                            String linkedCell = null;
                            String position = "Unknown";
                            
                            try {
                                // Try to get position
                                if (shape.getAnchor() != null) {
                                    XSSFAnchor anchor = shape.getAnchor();
                                    if (anchor instanceof XSSFClientAnchor) {
                                        XSSFClientAnchor clientAnchor = (XSSFClientAnchor) anchor;
                                        position = String.format("Row %d, Col %d", clientAnchor.getRow1(), clientAnchor.getCol1());
                                    }
                                }
                                
                                // Try to access the underlying XML to get checkbox state
                                java.lang.reflect.Method getCTShapeMethod = shape.getClass().getMethod("getCTShape");
                                Object ctShape = getCTShapeMethod.invoke(shape);
                                
                                if (ctShape != null) {
                                    // Get the XML string representation
                                    String xmlString = ctShape.toString();
                                    
                                    // Try to find linked cell in fmla
                                    if (xmlString.contains("fmla=")) {
                                        int fmlaStart = xmlString.indexOf("fmla=\"");
                                        if (fmlaStart != -1) {
                                            int fmlaEnd = xmlString.indexOf("\"", fmlaStart + 6);
                                            if (fmlaEnd != -1) {
                                                linkedCell = xmlString.substring(fmlaStart + 6, fmlaEnd);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("    Could not read checkbox state for {}: {}", shapeName, e.getMessage());
                            }
                            
                            // If we have a linked cell, check its boolean value
                            if (linkedCell != null && !linkedCell.isEmpty()) {
                                try {
                                    // Parse cell reference (e.g., "Data!$A$21" or "$A$21")
                                    String cellRef = linkedCell.replace("$", "");
                                    XSSFSheet targetSheet = sheet;
                                    
                                    if (cellRef.contains("!")) {
                                        String[] parts = cellRef.split("!");
                                        String targetSheetName = parts[0];
                                        cellRef = parts[1];
                                        targetSheet = workbook.getSheet(targetSheetName);
                                    }
                                    
                                    if (targetSheet != null) {
                                        // Parse cell reference (e.g., "A21")
                                        org.apache.poi.ss.util.CellReference ref = new org.apache.poi.ss.util.CellReference(cellRef);
                                        Row row = targetSheet.getRow(ref.getRow());
                                        if (row != null) {
                                            Cell cell = row.getCell(ref.getCol());
                                            if (cell != null && cell.getCellType() == CellType.BOOLEAN) {
                                                isChecked = cell.getBooleanCellValue();
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("    Could not read linked cell {}: {}", linkedCell, e.getMessage());
                                }
                            }
                            
                            totalCheckboxes++;
                            if (isChecked) {
                                checkedCheckboxes++;
                            }
                            
                            Map<String, Object> checkboxInfo = new HashMap<>();
                            checkboxInfo.put("sheet", sheetName);
                            checkboxInfo.put("name", shapeName);
                            checkboxInfo.put("checked", isChecked);
                            checkboxInfo.put("linkedCell", linkedCell != null ? linkedCell : "Not linked");
                            checkboxInfo.put("position", position);
                            
                            checkboxes.add(checkboxInfo);
                            
                            log.info("    ✓ {} ({}): {} [Linked: {}]", 
                                shapeName, position, 
                                isChecked ? "CHECKED" : "UNCHECKED",
                                linkedCell != null ? linkedCell : "None");
                        }
                    }
                }
            }
            
            log.info("\n========================================");
            log.info("SUMMARY:");
            log.info("  Total checkboxes found: {}", totalCheckboxes);
            log.info("  Checked: {}", checkedCheckboxes);
            log.info("  Unchecked: {}", totalCheckboxes - checkedCheckboxes);
            log.info("========================================\n");
            
                // Add summary of ctrlProps states
                int ctrlPropsChecked = (int) ctrlPropsStates.values().stream().filter(b -> b).count();
                int ctrlPropsTotal = ctrlPropsStates.size();
                
                result.put("totalCheckboxes", totalCheckboxes);
                result.put("checkedCheckboxes", checkedCheckboxes);
                result.put("uncheckedCheckboxes", totalCheckboxes - checkedCheckboxes);
                result.put("checkboxes", checkboxes);
                result.put("filePath", filePath);
                
                // Include ctrlProps analysis (the TRUE state from ZIP)
                result.put("ctrlPropsTotal", ctrlPropsTotal);
                result.put("ctrlPropsChecked", ctrlPropsChecked);
                result.put("ctrlPropsUnchecked", ctrlPropsTotal - ctrlPropsChecked);
                result.put("ctrlPropsStates", ctrlPropsStates);
                
                // Create a list of which specific ctrlProp files are checked
                java.util.List<String> checkedCtrlProps = new java.util.ArrayList<>();
                for (Map.Entry<String, Boolean> entry : ctrlPropsStates.entrySet()) {
                    if (entry.getValue()) {
                        // Extract the number from the filename (e.g., "ctrlProp6.xml" -> "6")
                        String fileName = entry.getKey();
                        String propNumber = fileName.replaceAll("[^0-9]", "");
                        checkedCtrlProps.add("ctrlProp" + propNumber);
                    }
                }
                result.put("checkedCtrlProps", checkedCtrlProps);
            
            } catch (Exception e) {
                log.error("Error analyzing Excel file for checkboxes", e);
                result.put("error", e.getMessage());
            }
        } finally {
            // Clean up temporary file
            if (tempFilePath != null) {
                try {
                    java.io.File tempFile = new java.io.File(tempFilePath);
                    if (tempFile.exists()) {
                        tempFile.delete();
                        log.info("Cleaned up temporary file: {}", tempFilePath);
                    }
                } catch (Exception e) {
                    log.warn("Could not delete temp file: {}", e.getMessage());
                }
            }
        }
        
        return result;
    }
    
    private String getColumnLetter(int columnIndex) {
        StringBuilder column = new StringBuilder();
        while (columnIndex >= 0) {
            column.insert(0, (char) ('A' + (columnIndex % 26)));
            columnIndex = (columnIndex / 26) - 1;
        }
        return column.toString();
    }
}

