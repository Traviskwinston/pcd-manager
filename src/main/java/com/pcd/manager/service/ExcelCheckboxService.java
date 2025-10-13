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
        String filePath = "uploads/reference-documents/Blank RMA_PCDMANAGER.xlsx";
        String outputPath = "uploads/reference-documents/Blank RMA_PCDMANAGER_CHECKED.xlsx";
        
        try (FileInputStream fis = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            
            log.info("========================================");
            log.info("TESTING: Finding the cell for Purged = NO");
            log.info("File: Blank RMA_PCDMANAGER.xlsx");
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
    
    private String getColumnLetter(int columnIndex) {
        StringBuilder column = new StringBuilder();
        while (columnIndex >= 0) {
            column.insert(0, (char) ('A' + (columnIndex % 26)));
            columnIndex = (columnIndex / 26) - 1;
        }
        return column.toString();
    }
}

