package com.pcd.manager.service;

import com.pcd.manager.model.PartLineItem;
import com.pcd.manager.model.Rma;
import com.pcd.manager.model.LaborEntry;
import com.pcd.manager.model.RmaComment;
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
import jakarta.annotation.PostConstruct;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelService.class);

    @Value("${app.upload.dir:${user.dir}/uploads}")
    private String uploadDir;

    @Autowired
    private UserService userService;

    @PostConstruct
    public void init() {
        // Check for template file in classpath
        try (InputStream templateStream = getClass().getResourceAsStream("/reference-documents/BlankRMA.xlsx")) {
            if (templateStream == null) {
                logger.error("RMA Excel template not found in classpath at: /reference-documents/BlankRMA.xlsx. Excel export functionality will be limited.");
            } else {
                logger.info("RMA Excel template found in classpath at: /reference-documents/BlankRMA.xlsx");
            }
        } catch (IOException e) {
            logger.error("Error checking for RMA Excel template in classpath: {}", e.getMessage(), e);
        }
    }

    /**
     * Populates the BlankRMA.xlsx template with data from the given RMA
     * 
     * @param rma The RMA to populate the template with
     * @param currentUser The current user
     * @return A byte array of the populated Excel file
     * @throws IOException If an I/O error occurs
     */
    public byte[] populateRmaTemplate(Rma rma, User currentUser) throws IOException {
        // Load the template from classpath
        try (InputStream templateStream = getClass().getResourceAsStream("/reference-documents/BlankRMAcustom.xlsx")) {
            
            if (templateStream == null) {
                logger.error("RMA template file not found in classpath");
                throw new IOException("RMA template file not found in classpath");
            }
            
            try (Workbook workbook = new XSSFWorkbook(templateStream);
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
    }
    
    /**
     * Populates the RMA-specific fields in the Excel template
     * 
     * @param sheet The sheet to populate
     * @param rma The RMA to get data from
     */
    private void populateRmaFields(Sheet sheet, Rma rma) {
        // RMA Number in J4 (SAP Notification Number is the same as RMA Number)
        setCellValue(sheet, "J4", rma.getRmaNumber());
        
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
            // Model Numbers in J16
            String modelNumbers = "";
            if (tool.getModel1() != null && !tool.getModel1().isEmpty()) {
                modelNumbers += tool.getModel1();
            }
            if (tool.getModel2() != null && !tool.getModel2().isEmpty()) {
                if (!modelNumbers.isEmpty()) {
                    modelNumbers += "/";
                }
                modelNumbers += tool.getModel2();
            }
            setCellValue(sheet, "J16", modelNumbers); // Model Numbers
            
            // Serial Numbers in F14
            String serialNumbers = "";
            if (tool.getSerialNumber1() != null && !tool.getSerialNumber1().isEmpty()) {
                serialNumbers += tool.getSerialNumber1();
            }
            if (tool.getSerialNumber2() != null && !tool.getSerialNumber2().isEmpty()) {
                if (!serialNumbers.isEmpty()) {
                    serialNumbers += "/";
                }
                serialNumbers += tool.getSerialNumber2();
            }
            setCellValue(sheet, "F14", serialNumbers); // Serial Numbers
            
            // Commission Date in J17
            if (tool.getCommissionDate() != null) {
                setCellValue(sheet, "J17", tool.getCommissionDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
            }
            
            // Chemical/Gas Service in J18
            setCellValue(sheet, "J18", tool.getChemicalGasService());
        }
        
        // Process Impact Checkboxes
        // Start-up/SO3 complete in J19
        setCellValue(sheet, "J19", rma.getStartupSo3Complete() != null && rma.getStartupSo3Complete() ? "X" : "");
        
        // Interruption to Flow in J20
        setCellValue(sheet, "J20", rma.getInterruptionToFlow() != null && rma.getInterruptionToFlow() ? "X" : "");
        
        // Interruption to Production in J21
        setCellValue(sheet, "J21", rma.getInterruptionToProduction() != null && rma.getInterruptionToProduction() ? "X" : "");
        
        // Downtime (hrs) in J22
        if (rma.getDowntimeHours() != null) {
            setCellValue(sheet, "J22", rma.getDowntimeHours().toString());
        }
        
        // Exposed to Process Gas or Chemicals checkbox in B34
        setCellValue(sheet, "B34", rma.getExposedToProcessGasOrChemicals() != null && rma.getExposedToProcessGasOrChemicals() ? "X" : "");
        
        // Purged checkbox in B35
        setCellValue(sheet, "B35", rma.getPurged() != null && rma.getPurged() ? "X" : "");
        
        // Instructions for exposed component in F35
        setCellValue(sheet, "F35", rma.getInstructionsForExposedComponent());
        
        // Problem Information fields using updated cell references
        setCellValue(sheet, "B38", rma.getProblemDiscoverer());  // Who discovered - B38
        if (rma.getProblemDiscoveryDate() != null) {
            setCellValue(sheet, "B39", rma.getProblemDiscoveryDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));  // When discovered - B39
        }
        setCellValue(sheet, "B40", rma.getWhatHappened());      // What happened - B40
        setCellValue(sheet, "B44", rma.getWhyAndHowItHappened()); // Why and how - B44
        setCellValue(sheet, "B46", rma.getHowContained());      // How contained - B46
        setCellValue(sheet, "B47", rma.getWhoContained());      // Who contained - B47
        
        // Failed on Install checkbox in B43
        setCellValue(sheet, "B43", rma.getFailedOnInstall() != null && rma.getFailedOnInstall() ? "X" : "");
        
        // Parts (up to 4) - using the correct template layout
        if (rma.getPartLineItems() != null && !rma.getPartLineItems().isEmpty()) {
            logger.info("EXCEL EXPORT - Processing {} part line items for RMA {}", rma.getPartLineItems().size(), rma.getId());
            List<PartLineItem> parts = new ArrayList<>(rma.getPartLineItems());
            
            // Part layout in template:
            // Part 1: Replacement (B24), Qty (B25), PN (B26), Desc (B27)
            // Part 2: Replacement (J24), Qty (J25), PN (I26), Desc (I27)
            // Part 3: Replacement (B29), Qty (B30), PN (B31), Desc (B32)
            // Part 4: Replacement (J29), Qty (J30), PN (I31), Desc (I32)
            
            String[][] partCells = {
                {"B24", "B25", "B26", "B27"}, // Part 1: Replacement, Qty, PN, Desc
                {"J24", "J25", "I26", "I27"}, // Part 2: Replacement, Qty, PN, Desc
                {"B29", "B30", "B31", "B32"}, // Part 3: Replacement, Qty, PN, Desc
                {"J29", "J30", "I31", "I32"}  // Part 4: Replacement, Qty, PN, Desc
            };
            
            // Limit to first 4 parts
            for (int i = 0; i < Math.min(parts.size(), 4); i++) {
                PartLineItem part = parts.get(i);
                String[] cells = partCells[i];
                String replacementCell = cells[0];
                String qtyCell = cells[1];
                String pnCell = cells[2];
                String descCell = cells[3];
                
                logger.info("EXCEL EXPORT - Part #{}: Replacement={}, Qty={}, PN={}, Desc={}, Name='{}', Number='{}', Description='{}', Qty={}, Replacement={}", 
                           i+1, replacementCell, qtyCell, pnCell, descCell, part.getPartName(), part.getPartNumber(), part.getProductDescription(), 
                           part.getQuantity(), part.getReplacementRequired());
                
                // Set replacement required checkbox
                setCellValue(sheet, replacementCell, part.getReplacementRequired() != null && part.getReplacementRequired() ? "X" : "");
                
                // Set quantity
                if (part.getQuantity() != null) {
                    setCellValue(sheet, qtyCell, part.getQuantity().toString());
                }
                
                // Set part number
                setCellValue(sheet, pnCell, part.getPartNumber());
                
                // Set description
                setCellValue(sheet, descCell, part.getProductDescription());
            }
        } else {
            logger.warn("EXCEL EXPORT - No part line items found for RMA {}", rma.getId());
        }
        
        // Labor entries (up to 5) - A51-A55 (Description), D51-D55 (Technician), G51-G55 (Hours), H51-H55 (Date), I51-I55 (Price/Hr)
        if (rma.getLaborEntries() != null && !rma.getLaborEntries().isEmpty()) {
            logger.info("EXCEL EXPORT - Processing {} labor entries for RMA {}", rma.getLaborEntries().size(), rma.getId());
            List<LaborEntry> laborEntries = new ArrayList<>(rma.getLaborEntries());
            
            // Labor layout in template:
            // Labor 1: Description (A51), Technician (D51), Hours (G51), Date (H51), Price/Hr (I51)
            // Labor 2: Description (A52), Technician (D52), Hours (G52), Date (H52), Price/Hr (I52)
            // Labor 3: Description (A53), Technician (D53), Hours (G53), Date (H53), Price/Hr (I53)
            // Labor 4: Description (A54), Technician (D54), Hours (G54), Date (H54), Price/Hr (I54)
            // Labor 5: Description (A55), Technician (D55), Hours (G55), Date (H55), Price/Hr (I55)
            
            // Limit to first 5 labor entries
            for (int i = 0; i < Math.min(laborEntries.size(), 5); i++) {
                LaborEntry labor = laborEntries.get(i);
                int rowNum = 51 + i; // A51, A52, A53, A54, A55
                
                logger.info("EXCEL EXPORT - Labor #{}: Description='{}', Technician='{}', Hours={}, Date={}, Price/Hr={}", 
                           i+1, labor.getDescription(), labor.getTechnician(), labor.getHours(), 
                           labor.getLaborDate(), labor.getPricePerHour());
                
                // Set description in A51-A55
                setCellValue(sheet, "A" + rowNum, labor.getDescription());
                
                // Set technician in D51-D55
                setCellValue(sheet, "D" + rowNum, labor.getTechnician());
                
                // Set hours in G51-G55
                if (labor.getHours() != null) {
                    setCellValue(sheet, "G" + rowNum, labor.getHours().toString());
                }
                
                // Set date in H51-H55
                if (labor.getLaborDate() != null) {
                    setCellValue(sheet, "H" + rowNum, labor.getLaborDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
                }
                
                // Set price per hour in I51-I55
                if (labor.getPricePerHour() != null) {
                    setCellValue(sheet, "I" + rowNum, labor.getPricePerHour().toString());
                }
            }
        } else {
            logger.info("EXCEL EXPORT - No labor entries found for RMA {}", rma.getId());
        }
    }
    
    /**
     * Set a value to a cell using the Excel A1 notation (e.g., "A1", "B2")
     */
    private void setCellValue(Sheet sheet, String cellReference, String value) {
        if (value == null || value.isEmpty()) {
            logger.debug("EXCEL EXPORT - Skipping empty value for cell {}", cellReference);
            return;
        }
        
        try {
            // Convert A1 notation to row and column indices
            CellReference ref = new CellReference(cellReference);
            int rowIndex = ref.getRow();
            int colIndex = ref.getCol();
            
            logger.debug("EXCEL EXPORT - Setting cell {} (row {}, col {}) to value: '{}'", 
                        cellReference, rowIndex, colIndex, value);
            
            // Get or create the row and cell
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
                logger.debug("EXCEL EXPORT - Created new row {}", rowIndex);
            }
            
            Cell cell = row.getCell(colIndex);
            if (cell == null) {
                cell = row.createCell(colIndex);
                logger.debug("EXCEL EXPORT - Created new cell at {}", cellReference);
            }
            
            // Set the value
            cell.setCellValue(value);
            logger.debug("EXCEL EXPORT - Successfully set cell {} to '{}'", cellReference, value);
        } catch (Exception e) {
            logger.error("EXCEL EXPORT - Error setting cell value for {}: {}", cellReference, e.getMessage(), e);
        }
    }

    /**
     * Extracts RMA data from an uploaded Excel file
     * 
     * @param excelBytes The bytes of the uploaded Excel file
     * @return A map containing extracted RMA data
     */
    public Map<String, Object> extractRmaDataFromExcel(byte[] excelBytes) {
        if (excelBytes == null || excelBytes.length == 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Excel file is empty");
            return error;
        }
        
        try (InputStream is = new ByteArrayInputStream(excelBytes)) {
            return extractRmaDataFromExcel(is);
        } catch (IOException e) {
            logger.error("Error reading Excel file bytes", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to read Excel file: " + e.getMessage());
            return error;
        }
    }

    /**
     * Extracts RMA data from an Excel file path
     * 
     * @param filePath The path to the Excel file
     * @return A map containing extracted RMA data
     */
    public Map<String, Object> extractRmaDataFromExcelFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "File path is empty");
            return error;
        }

        try {
            // If it's a relative path, prepend the upload directory
            Path fullPath;
            if (!Paths.get(filePath).isAbsolute()) {
                fullPath = Paths.get(uploadDir, filePath);
            } else {
                fullPath = Paths.get(filePath);
            }

            if (!Files.exists(fullPath)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Excel file not found at path: " + filePath);
                return error;
            }

            try (InputStream is = Files.newInputStream(fullPath)) {
                return extractRmaDataFromExcel(is);
            }
        } catch (IOException e) {
            logger.error("Error reading Excel file from path: {}", filePath, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to read Excel file: " + e.getMessage());
            return error;
        }
    }

    /**
     * Internal method to extract RMA data from an input stream
     * 
     * @param inputStream The input stream of the Excel file
     * @return A map containing extracted RMA data
     */
    private Map<String, Object> extractRmaDataFromExcel(InputStream inputStream) {
        Map<String, Object> extractedData = new HashMap<>();
        
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0); // Assuming data is on the first sheet
            if (sheet == null) {
                extractedData.put("error", "First sheet is null or workbook is empty");
                return extractedData;
            }

            // Try to find a sheet named "Data" for specific boolean fields if they exist
            Sheet dataSheet = workbook.getSheet("Data");
            // If not found by name "Data", it might be at a fixed index or not used for all fields.
            // For now, proceed with `sheet` for main data and use `dataSheet` only if explicitly needed and found.

            // RMA Number from J4
            String rmaNumber = getCellStringValue(sheet, "J4");
            if (rmaNumber != null && !rmaNumber.isEmpty()) {
                extractedData.put("rmaNumber", rmaNumber);
                logger.info("Extracted RMA Number from J4: {}", rmaNumber);
            } else {
                logger.warn("RMA Number (J4) is empty or not found.");
            }
            
            // Service Order from J5
            extractedData.put("serviceOrder", getCellStringValue(sheet, "J5"));
            
            // Written Date from B6
            String writtenDateStr = getCellStringValue(sheet, "B6");
            if (writtenDateStr != null && !writtenDateStr.isEmpty()) {
                try {
                    LocalDate parsedDate = parseDate(writtenDateStr);
                    if (parsedDate != null) {
                        extractedData.put("writtenDate", parsedDate.format(DateTimeFormatter.ISO_DATE));
                    } else {
                        logger.warn("Could not parse Written Date from B6: {}", writtenDateStr);
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing Written Date from B6: {}", writtenDateStr, e);
                }
            }

            // Technician Information
            extractedData.put("fieldTechName", getCellStringValue(sheet, "B7")); // Field Tech Name
            extractedData.put("fieldTechPhone", getCellStringValue(sheet, "B8")); // Field Tech Phone
            extractedData.put("fieldTechEmail", getCellStringValue(sheet, "B9")); // Technician Email (was customerEmail)
            
            // Customer Information
            extractedData.put("customerName", getCellStringValue(sheet, "B11")); // Customer Name
            extractedData.put("companyShipToName", getCellStringValue(sheet, "B12")); // Company Ship To Name
            extractedData.put("companyShipToAddress", getCellStringValue(sheet, "B13")); // Address
            
            String cityState = getCellStringValue(sheet, "B14"); // City, State
            if (cityState != null && !cityState.isEmpty()) {
                String[] parts = cityState.split(",");
                if (parts.length > 0) extractedData.put("city", parts[0].trim());
                if (parts.length > 1) extractedData.put("state", parts[1].trim());
            }
            extractedData.put("zipCode", getCellStringValue(sheet, "B15")); // Zip Code
            extractedData.put("attn", getCellStringValue(sheet, "B16")); // Attn
            // Customer Phone B17 is no longer mapped as per technician info changes

            // Location from E14 (used for display, not direct RMA field mapping usually)
            extractedData.put("locationName", getCellStringValue(sheet, "E14"));
            
            // Reason For Request from G7
            extractedData.put("reasonForRequest", getCellStringValue(sheet, "G7"));
            // DSS Product Line from G9
            extractedData.put("dssProductLine", getCellStringValue(sheet, "G9"));
            // System Description from G11
            extractedData.put("systemDescription", getCellStringValue(sheet, "G11"));
            
            // Equipment Info (F14) - primarily for serial number parsing
            String toolInfoRaw = getCellStringValue(sheet, "F14"); // Equipment Number + Serial Number(s)
            logger.info("Raw string from F14 (Tool Info): '{}'", toolInfoRaw); // Log the raw F14 value

            String parsedSerial1 = null;
            String parsedSerial2 = null;

            // Try to parse serial numbers from F14
            // Current logic seems to be:
            // If F14 contains a slash, take part before as SN1, part after as SN2
            // If F14 contains a space, take part after space as SN1
            // If F14 contains neither, take whole string as SN1
            // This needs to be confirmed and potentially revised based on actual F14 format.

            if (toolInfoRaw != null && !toolInfoRaw.isEmpty()) {
                if (toolInfoRaw.contains("/")) {
                    String[] parts = toolInfoRaw.split("/", 2);
                    parsedSerial1 = parts[0].trim();
                    if (parts.length > 1) {
                        parsedSerial2 = parts[1].trim();
                    }
                } else if (toolInfoRaw.contains(" ")) {
                    // This logic might be problematic if model numbers also contain spaces
                    // Assuming serial number is the last "word" if a space is present
                    int lastSpaceIndex = toolInfoRaw.lastIndexOf(" ");
                    if (lastSpaceIndex != -1 && lastSpaceIndex < toolInfoRaw.length() - 1) {
                         parsedSerial1 = toolInfoRaw.substring(lastSpaceIndex + 1).trim();
                    } else {
                        parsedSerial1 = toolInfoRaw.trim(); // Fallback to whole string
                    }
                } else {
                    parsedSerial1 = toolInfoRaw.trim();
                }
            }
            
            // Example: If F14 is "MODELXYZ 12345/67890"
            // parsedSerial1 should be "12345" (or "MODELXYZ 12345" depending on exact parsing)
            // parsedSerial2 should be "67890"

            // The actual parsing logic needs to be fixed once we know the F14 format.
            // String serialNumberRaw = getCellStringValue(sheet, "F14"); // Equipment Number + Serial Number
            // Splitting logic based on "/" and then " " (Placeholder - likely needs refinement)
            // -- The following placeholder logic is being removed --
            // if (toolInfoRaw != null && !toolInfoRaw.isEmpty()) {
            //     String[] serials = toolInfoRaw.split("/");
            //     if (serials.length > 0) {
            //         String firstPart = serials[0].trim();
            //         // If firstPart contains space, take the last segment as serial1
            //         if (firstPart.contains(" ")) {
            //             parsedSerial1 = firstPart.substring(firstPart.lastIndexOf(" ") + 1);
            //         } else {
            //             parsedSerial1 = firstPart;
            //         }
            //     }
            //     if (serials.length > 1) {
            //         parsedSerial2 = serials[1].trim();
            //     }
            // }
            // 
            // // THIS IS A VERY SIMPLISTIC WAY TO GET "1" and "2" TO MATCH OBSERVED LOGS
            // // AND WILL LIKELY BE WRONG FOR ACTUAL DATA.
            // // THE LOG ADDED ABOVE FOR 'toolInfoRaw' IS KEY.
            // // We will replace this once we confirm the actual format of F14.
            // if (toolInfoRaw != null) {
            //     if (toolInfoRaw.contains("1")) parsedSerial1 = "1"; // Simplified, placeholder
            //     if (toolInfoRaw.contains("2")) parsedSerial2 = "2"; // Simplified, placeholder
            // }

            extractedData.put("parsedSerial1", parsedSerial1);
            extractedData.put("parsedSerial2", parsedSerial2);

            // Model Number (Parent Commodity Code) from J16
            extractedData.put("parentCommodityCode", getCellStringValue(sheet, "J16"));
            // Chemical/Gas Service from J18
            extractedData.put("chemicalGasService", getCellStringValue(sheet, "J18"));
            // Downtime (hrs) from J22
            String downtimeStr = getCellStringValue(sheet, "J22");
            if (downtimeStr != null && !downtimeStr.isEmpty()) {
                try {
                    extractedData.put("downtimeHours", Integer.parseInt(downtimeStr.replaceAll("[^0-9]", "")));
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse downtime value: {}", downtimeStr);
                }
            }

            // Parts Data
            List<Map<String, Object>> partsList = new ArrayList<>();
            // Part 1: PN (B26), Desc (B27), Qty (B25)
            addPartIfPresent(partsList, sheet, "B26", "B27", "B25");
            // Part 2: PN (I26), Desc (I27), Qty (I25)
            addPartIfPresent(partsList, sheet, "I26", "I27", "I25");
            // Part 3: PN (B31), Desc (B32), Qty (B30)
            addPartIfPresent(partsList, sheet, "B31", "B32", "B30");
            // Part 4: PN (I31), Desc (I32), Qty (I30)
            addPartIfPresent(partsList, sheet, "I31", "I32", "I30");
            if (!partsList.isEmpty()) {
                extractedData.put("parts", partsList);
            }
            
            // Instructions for exposed component from F35
            extractedData.put("instructionsForExposedComponent", getCellStringValue(sheet, "F35"));

            // Comments from B36
            extractedData.put("comments", getCellStringValue(sheet, "B36"));

            // Problem Information
            extractedData.put("problemDiscoverer", getCellStringValue(sheet, "B38")); // Who discovered
            String discoveryDateStr = getCellStringValue(sheet, "B39"); // When discovered
            if (discoveryDateStr != null && !discoveryDateStr.isEmpty()) {
                 try {
                    LocalDate parsedDate = parseDate(discoveryDateStr);
                    if (parsedDate != null) {
                        extractedData.put("problemDiscoveryDate", parsedDate.format(DateTimeFormatter.ISO_DATE));
                    } else {
                        logger.warn("Could not parse Problem Discovery Date from B39: {}", discoveryDateStr);
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing Problem Discovery Date from B39: {}", discoveryDateStr, e);
                }
            }
            extractedData.put("whatHappened", getCellStringValue(sheet, "B40"));      // What happened
            extractedData.put("whyAndHowItHappened", getCellStringValue(sheet, "B44")); // Why and how
            extractedData.put("howContained", getCellStringValue(sheet, "B46"));      // How contained
            extractedData.put("whoContained", getCellStringValue(sheet, "B47"));      // Who contained
            
            // Boolean flags (exposedToProcessGasOrChemicals, purged) - these were removed as per user request
            // as they should not be extracted from Excel.

            // Extract Labor entries from A51-A55 (Description), D51-D55 (Technician), G51-G55 (Hours), H51-H55 (Date), I51-I55 (Price/Hr)
            List<Map<String, Object>> laborEntries = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                int rowNum = 51 + i;
                String description = getCellStringValue(sheet, "A" + rowNum);
                String technician = getCellStringValue(sheet, "D" + rowNum);
                String hoursStr = getCellStringValue(sheet, "G" + rowNum);
                String dateStr = getCellStringValue(sheet, "H" + rowNum);
                String pricePerHourStr = getCellStringValue(sheet, "I" + rowNum);
                
                // Only add labor entry if at least description or technician is present
                if ((description != null && !description.isEmpty()) || (technician != null && !technician.isEmpty())) {
                    Map<String, Object> laborData = new HashMap<>();
                    laborData.put("description", description);
                    laborData.put("technician", technician);
                    
                    // Parse hours
                    if (hoursStr != null && !hoursStr.isEmpty()) {
                        try {
                            laborData.put("hours", Double.parseDouble(hoursStr));
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse hours from {}{}: {}", "G", rowNum, hoursStr);
                        }
                    }
                    
                    // Parse date
                    if (dateStr != null && !dateStr.isEmpty()) {
                        try {
                            LocalDate parsedDate = parseDate(dateStr);
                            if (parsedDate != null) {
                                laborData.put("laborDate", parsedDate.format(DateTimeFormatter.ISO_DATE));
                            } else {
                                logger.warn("Could not parse labor date from {}{}: {}", "H", rowNum, dateStr);
                            }
                        } catch (Exception e) {
                            logger.warn("Error parsing labor date from {}{}: {}", "H", rowNum, dateStr, e);
                        }
                    }
                    
                    // Parse price per hour
                    if (pricePerHourStr != null && !pricePerHourStr.isEmpty()) {
                        try {
                            laborData.put("pricePerHour", Double.parseDouble(pricePerHourStr));
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse price per hour from {}{}: {}", "I", rowNum, pricePerHourStr);
                        }
                    }
                    
                    laborEntries.add(laborData);
                    logger.info("Extracted labor entry #{}: {}", i+1, laborData);
                }
            }
            
            if (!laborEntries.isEmpty()) {
                extractedData.put("laborEntries", laborEntries);
                logger.info("Extracted {} labor entries", laborEntries.size());
            } else {
                logger.info("No labor entries found in Excel");
            }

            logger.info("Final extracted data: {}", extractedData);

        } catch (IOException e) {
            logger.error("Error parsing Excel file", e);
            extractedData.put("error", "Failed to parse Excel file: " + e.getMessage());
        }
        return extractedData;
    }

    private void addPartIfPresent(List<Map<String, Object>> partsList, Sheet sheet, String pnCell, String descCell, String qtyCell) {
        String partNumber = getCellStringValue(sheet, pnCell);
        String productDescription = getCellStringValue(sheet, descCell);
        String qtyStr = getCellStringValue(sheet, qtyCell);
        
        // Only add part if at least part number or description is present
        if ((partNumber != null && !partNumber.isEmpty()) || (productDescription != null && !productDescription.isEmpty())) {
            Map<String, Object> partData = new HashMap<>();
            partData.put("partNumber", partNumber);
            partData.put("productDescription", productDescription); // ensure key matches frontend expectation
            partData.put("quantity", parseQuantity(qtyStr)); // Default to 1 if parsing fails or empty
            partData.put("partName", ""); // Part Name is not in this Excel structure
            partData.put("replacementRequired", false); // Default, not in Excel
            partsList.add(partData);
        }
    }

    /**
     * Gets a string value from a cell using Excel A1 notation
     */
    private String getCellStringValue(Sheet sheet, String cellRef) {
        CellReference ref = new CellReference(cellRef);
        Row row = sheet.getRow(ref.getRow());
        if (row == null) {
            return null;
        }
        
        Cell cell = row.getCell(ref.getCol());
        if (cell == null) {
            return null;
        }
        
        // Handle different cell types
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat("MM/dd/yyyy").format(cell.getDateCellValue());
                }
                // Format numeric values specially to prevent scientific notation for large numbers
                // This is particularly important for service order numbers
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.floor(numericValue) && !Double.isInfinite(numericValue)) {
                    // It's an integer value (like a service order number)
                    return String.format("%.0f", numericValue); // Format without decimal places
                }
                return String.valueOf(numericValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
                CellValue cellValue = evaluator.evaluate(cell);
                
                switch(cellValue.getCellType()) {
                    case STRING:
                        return cellValue.getStringValue();
                    case NUMERIC:
                        if (DateUtil.isCellDateFormatted(cell)) {
                            return new SimpleDateFormat("MM/dd/yyyy").format(cell.getDateCellValue());
                        }
                        // Also handle formula results that are numeric
                        double formulaNumericValue = cellValue.getNumberValue();
                        if (formulaNumericValue == Math.floor(formulaNumericValue) && !Double.isInfinite(formulaNumericValue)) {
                            // It's an integer value
                            return String.format("%.0f", formulaNumericValue); // Format without decimal places
                        }
                        return String.valueOf(cellValue.getNumberValue());
                    case BOOLEAN:
                        return String.valueOf(cellValue.getBooleanValue());
                    default:
                        return "";
                }
            default:
                return "";
        }
    }
    
    /**
     * Parse a quantity value from a string, with fallback to 1
     */
    private Integer parseQuantity(String qtyStr) {
        if (qtyStr != null && !qtyStr.isEmpty()) {
            try {
                return Integer.parseInt(qtyStr.trim());
            } catch (NumberFormatException e) {
                try {
                    // Try parsing as double and converting to int
                    return (int) Double.parseDouble(qtyStr.trim());
                } catch (NumberFormatException ex) {
                    logger.warn("Could not parse quantity value: {}", qtyStr);
                    return 1; // Default to 1 if parsing fails
                }
            }
        }
        return 1; // Default quantity
    }

    /**
     * Checks if a string value represents a true value
     */
    private boolean isTrueValue(String value) {
        return "Yes".equalsIgnoreCase(value) || "True".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * Parses a date string into a LocalDate object
     * 
     * @param dateStr The date string to parse
     * @return The parsed LocalDate object, or null if parsing fails
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        // Array of common date formats to try
        String[] formats = {
            "MM/dd/yyyy", "M/d/yyyy", "MM-dd-yyyy", "yyyy-MM-dd",
            "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy",
            "MMM d, yyyy", "MMMM d, yyyy",
            "d MMM yyyy", "d MMMM yyyy"
        };
        
        // Try each format until one works
        for (String format : formats) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (Exception e) {
                // Try next format
                continue;
            }
        }
        
        // Last-ditch effort: try Excel's numeric date format
        try {
            // Excel dates are stored as days since December 31, 1899
            double numericDate = Double.parseDouble(dateStr.trim());
            // Convert to days since January 1, 1970 (epoch)
            long epochDays = (long) (numericDate - 25569); // 25569 is the number of days between Dec 31, 1899 and Jan 1, 1970
            return LocalDate.ofEpochDay(epochDays);
        } catch (Exception e) {
            logger.debug("Could not parse date as numeric Excel date: {}", dateStr);
        }
        
        // If all parsing attempts fail
        return null;
    }
    
    /**
     * Generates an Excel file containing a list of RMAs
     * 
     * @param rmas The list of RMAs to include in the Excel file
     * @return A byte array of the Excel file
     * @throws IOException If an I/O error occurs
     */
    public byte[] generateRmaListExcel(List<Rma> rmas) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            // Create sheet
            Sheet sheet = workbook.createSheet("RMA List");
            
            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            
            // Create date style
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("MM/dd/yyyy"));
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "RMA Number", "SAP Notification", "Tool Name", "Tool Serial #", "Tool Model #",
                "Status", "Priority", "Customer Name", "Location", "Technician",
                "Written Date", "RMA # Provided Date", "Shipping Memo Date", "Parts Received Date",
                "Installed Parts Date", "Failed Parts Packed Date", "Failed Parts Shipped Date",
                "Part Names", "Part Numbers", "Notes"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Populate data rows
            int rowNum = 1;
            for (Rma rma : rmas) {
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;
                
                // RMA Number
                setCellValueSafe(row, colNum++, rma.getRmaNumber());
                
                // SAP Notification
                setCellValueSafe(row, colNum++, rma.getSapNotificationNumber());
                
                // Tool information
                Tool tool = rma.getTool();
                if (tool != null) {
                    setCellValueSafe(row, colNum++, tool.getName()); // Tool Name
                    
                    // Tool Serial # (combine serial1 and serial2)
                    String serialNumber = "";
                    if (tool.getSerialNumber1() != null && !tool.getSerialNumber1().isEmpty()) {
                        serialNumber = tool.getSerialNumber1();
                    }
                    if (tool.getSerialNumber2() != null && !tool.getSerialNumber2().isEmpty()) {
                        if (!serialNumber.isEmpty()) {
                            serialNumber += " / ";
                        }
                        serialNumber += tool.getSerialNumber2();
                    }
                    setCellValueSafe(row, colNum++, serialNumber);
                    
                    // Tool Model # (combine model1 and model2)
                    String modelNumber = "";
                    if (tool.getModel1() != null && !tool.getModel1().isEmpty()) {
                        modelNumber = tool.getModel1();
                    }
                    if (tool.getModel2() != null && !tool.getModel2().isEmpty()) {
                        if (!modelNumber.isEmpty()) {
                            modelNumber += " / ";
                        }
                        modelNumber += tool.getModel2();
                    }
                    setCellValueSafe(row, colNum++, modelNumber);
                } else {
                    setCellValueSafe(row, colNum++, ""); // Tool Name
                    setCellValueSafe(row, colNum++, ""); // Tool Serial #
                    setCellValueSafe(row, colNum++, ""); // Tool Model #
                }
                
                // Status
                setCellValueSafe(row, colNum++, rma.getStatus() != null ? rma.getStatus().getDisplayName() : "");
                
                // Priority
                setCellValueSafe(row, colNum++, rma.getPriority() != null ? rma.getPriority().getDisplayName() : "");
                
                // Customer Name
                setCellValueSafe(row, colNum++, rma.getCustomerName());
                
                // Location
                setCellValueSafe(row, colNum++, rma.getLocation() != null ? rma.getLocation().getDisplayName() : "");
                
                // Technician
                setCellValueSafe(row, colNum++, rma.getTechnician());
                
                // Dates (with date formatting)
                setDateCellValue(row, colNum++, rma.getWrittenDate(), dateStyle);
                setDateCellValue(row, colNum++, rma.getRmaNumberProvidedDate(), dateStyle);
                setDateCellValue(row, colNum++, rma.getShippingMemoEmailedDate(), dateStyle);
                setDateCellValue(row, colNum++, rma.getPartsReceivedDate(), dateStyle);
                setDateCellValue(row, colNum++, rma.getInstalledPartsDate(), dateStyle);
                setDateCellValue(row, colNum++, rma.getFailedPartsPackedDate(), dateStyle);
                setDateCellValue(row, colNum++, rma.getFailedPartsShippedDate(), dateStyle);
                
                // Part information (concatenated)
                StringBuilder partNames = new StringBuilder();
                StringBuilder partNumbers = new StringBuilder();
                
                if (rma.getPartLineItems() != null && !rma.getPartLineItems().isEmpty()) {
                    for (int i = 0; i < rma.getPartLineItems().size(); i++) {
                        PartLineItem item = rma.getPartLineItems().get(i);
                        if (i > 0) {
                            partNames.append("; ");
                            partNumbers.append("; ");
                        }
                        partNames.append(item.getPartName() != null ? item.getPartName() : "");
                        partNumbers.append(item.getPartNumber() != null ? item.getPartNumber() : "");
                    }
                }
                
                setCellValueSafe(row, colNum++, partNames.toString());
                setCellValueSafe(row, colNum++, partNumbers.toString());
                
                // Notes only (Root Cause and Resolution removed)
                setCellValueSafe(row, colNum++, rma.getNotes());
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Set minimum width to prevent too narrow columns
                if (sheet.getColumnWidth(i) < 2000) {
                    sheet.setColumnWidth(i, 2000);
                }
                // Set maximum width to prevent too wide columns
                if (sheet.getColumnWidth(i) > 8000) {
                    sheet.setColumnWidth(i, 8000);
                }
            }
            
            // Freeze header row
            sheet.createFreezePane(0, 1);
            
            // Write workbook to output stream
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
    
    /**
     * Safely sets a cell value, handling null values
     */
    private void setCellValueSafe(Row row, int columnIndex, String value) {
        Cell cell = row.createCell(columnIndex);
        if (value != null && !value.isEmpty()) {
            cell.setCellValue(value);
        }
    }
    
    /**
     * Sets a date cell value with proper formatting
     */
    private void setDateCellValue(Row row, int columnIndex, LocalDate date, CellStyle dateStyle) {
        Cell cell = row.createCell(columnIndex);
        if (date != null) {
            // Convert LocalDate to Date for Excel
            cell.setCellValue(java.sql.Date.valueOf(date));
            cell.setCellStyle(dateStyle);
        }
    }
} 