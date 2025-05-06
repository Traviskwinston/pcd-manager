package com.pcd.manager.service;

import com.pcd.manager.model.PartLineItem;
import com.pcd.manager.model.Rma;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelService.class);

    @Value("${app.upload.dir:${user.dir}/uploads}")
    private String uploadDir;

    @Autowired
    private UserService userService;

    /**
     * Populates the BlankRMA.xlsx template with data from the given RMA
     * 
     * @param rma The RMA to populate the template with
     * @param currentUser The current user
     * @return A byte array of the populated Excel file
     * @throws IOException If an I/O error occurs
     */
    public byte[] populateRmaTemplate(Rma rma, User currentUser) throws IOException {
        // Load the template
        Path templatePath = Paths.get(uploadDir, "reference-documents", "BlankRMA.xlsx");
        
        if (!Files.exists(templatePath)) {
            logger.error("RMA template file not found at: {}", templatePath);
            throw new IOException("RMA template file not found");
        }
        
        try (FileInputStream fis = new FileInputStream(templatePath.toFile());
             Workbook workbook = new XSSFWorkbook(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            // Get the first sheet
            Sheet sheet = workbook.getSheetAt(0);
            
            // Set today's date in B6
            setCellValue(sheet, "B6", LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
            
            // Set current user name in B7
            if (currentUser != null) {
                setCellValue(sheet, "B7", currentUser.getName());
                
                // Set user phone in B8 if available
                if (currentUser.getPhoneNumber() != null && !currentUser.getPhoneNumber().isEmpty()) {
                    setCellValue(sheet, "B8", currentUser.getPhoneNumber());
                }
                
                // Set user email in B9
                if (currentUser.getEmail() != null && !currentUser.getEmail().isEmpty()) {
                    setCellValue(sheet, "B9", currentUser.getEmail());
                }
            }
            
            // If RMA is provided, populate RMA-specific fields
            if (rma != null) {
                populateRmaFields(sheet, rma);
            }
            
            // Write the workbook to the output stream
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
    
    /**
     * Populates the RMA-specific fields in the Excel template
     * 
     * @param sheet The sheet to populate
     * @param rma The RMA to get data from
     */
    private void populateRmaFields(Sheet sheet, Rma rma) {
        // RMA Number in B4
        setCellValue(sheet, "B4", rma.getRmaNumber());
        
        // SAP Notification Number in J4
        setCellValue(sheet, "J4", rma.getSapNotificationNumber());
        
        // Service Order in J5
        setCellValue(sheet, "J5", rma.getServiceOrder());
        
        // Reason for Request in G7
        if (rma.getReasonForRequest() != null) {
            setCellValue(sheet, "G7", rma.getReasonForRequest().getDisplayName());
        }
        
        // DSS Product Line in G9
        if (rma.getDssProductLine() != null) {
            setCellValue(sheet, "G9", rma.getDssProductLine().getDisplayName());
        }
        
        // System Description in G11
        if (rma.getSystemDescription() != null) {
            setCellValue(sheet, "G11", rma.getSystemDescription().getDisplayName());
        }
        
        // Customer Information
        setCellValue(sheet, "B11", rma.getCustomerName()); // Customer Name
        setCellValue(sheet, "B12", rma.getCompanyShipToName()); // Company Ship To Name
        setCellValue(sheet, "B13", rma.getCompanyShipToAddress()); // Address
        
        // City and State in B14
        String cityState = "";
        if (rma.getCity() != null && !rma.getCity().isEmpty()) {
            cityState += rma.getCity();
        }
        if (rma.getState() != null && !rma.getState().isEmpty()) {
            if (!cityState.isEmpty()) {
                cityState += ", ";
            }
            cityState += rma.getState();
        }
        setCellValue(sheet, "B14", cityState); // City, State
        
        // Zip Code in B15
        setCellValue(sheet, "B15", rma.getZipCode()); // Zip Code
        
        // Attn in B16
        setCellValue(sheet, "B16", rma.getAttn()); // Attn
        
        // Contact Phone Number in B17
        setCellValue(sheet, "B17", rma.getCustomerPhone()); // Contact Phone Number
        
        // Tool Information
        Tool tool = rma.getTool();
        if (tool != null) {
            // Equipment Number(s) with Serial Number in F14
            String equipmentInfo = "";
            if (tool.getModel1() != null && !tool.getModel1().isEmpty()) {
                equipmentInfo += tool.getModel1();
            }
            if (tool.getModel2() != null && !tool.getModel2().isEmpty()) {
                equipmentInfo += "/" + tool.getModel2();
            }
            
            if (!equipmentInfo.isEmpty()) {
                equipmentInfo += " ";
            }
            
            if (tool.getSerialNumber1() != null && !tool.getSerialNumber1().isEmpty()) {
                equipmentInfo += tool.getSerialNumber1();
            }
            if (tool.getSerialNumber2() != null && !tool.getSerialNumber2().isEmpty()) {
                equipmentInfo += "/" + tool.getSerialNumber2();
            }
            
            setCellValue(sheet, "F14", equipmentInfo); // Equipment Number(s) + Serial Number
            
            // Commission Date in J17
            if (tool.getCommissionDate() != null) {
                setCellValue(sheet, "J17", tool.getCommissionDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
            }
            
            // Chemical/Gas Service in J18
            setCellValue(sheet, "J18", tool.getChemicalGasService());
        }
        
        // Downtime (hrs) in J22
        if (rma.getDowntimeHours() != null) {
            setCellValue(sheet, "J22", rma.getDowntimeHours().toString());
        }
        
        // Instructions for exposed component in F35
        setCellValue(sheet, "F35", rma.getInstructionsForExposedComponent());
        
        // Description in D33 (different cell to avoid overwriting the checkbox)
        setCellValue(sheet, "D33", rma.getDescription());
        
        // Comments - moved to B36 based on Excel template structure
        setCellValue(sheet, "B36", rma.getComments());
        
        // Parts (up to 4)
        if (rma.getPartLineItems() != null && !rma.getPartLineItems().isEmpty()) {
            List<PartLineItem> parts = new ArrayList<>(rma.getPartLineItems());
            // Limit to first 4 parts
            for (int i = 0; i < Math.min(parts.size(), 4); i++) {
                PartLineItem part = parts.get(i);
                int rowNum = 42 + i; // Starting at row 42 for first part
                
                setCellValue(sheet, "A" + rowNum, part.getPartName()); // Part Name
                setCellValue(sheet, "B" + rowNum, part.getPartNumber()); // Part Number
                setCellValue(sheet, "C" + rowNum, part.getProductDescription()); // Description
                if (part.getQuantity() != null) {
                    setCellValue(sheet, "D" + rowNum, part.getQuantity().toString()); // Qty
                }
                if (part.getReplacementRequired() != null) {
                    setCellValue(sheet, "E" + rowNum, part.getReplacementRequired() ? "Yes" : "No"); // Replacement Required
                }
            }
        }
    }
    
    /**
     * Set a value to a cell using the Excel A1 notation (e.g., "A1", "B2")
     */
    private void setCellValue(Sheet sheet, String cellReference, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        
        try {
            // Convert A1 notation to row and column indices
            CellReference ref = new CellReference(cellReference);
            int rowIndex = ref.getRow();
            int colIndex = ref.getCol();
            
            // Get or create the row and cell
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }
            
            Cell cell = row.getCell(colIndex);
            if (cell == null) {
                cell = row.createCell(colIndex);
            }
            
            // Set the value
            cell.setCellValue(value);
        } catch (Exception e) {
            logger.error("Error setting cell value for {}: {}", cellReference, e.getMessage());
        }
    }
} 