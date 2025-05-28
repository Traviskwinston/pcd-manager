package com.pcd.manager.service;

import com.pcd.manager.model.PartLineItem;
import com.pcd.manager.model.Rma;
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
import java.io.FileInputStream;
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
        // Check for template file
        Path templatePath = Paths.get(uploadDir, "reference-documents", "BlankRMA.xlsx");
        if (!Files.exists(templatePath)) {
            logger.error("RMA Excel template not found at: {}. Excel export functionality will be limited.", templatePath);
            try {
                // Create the directory if it doesn't exist
                Files.createDirectories(templatePath.getParent());
                logger.info("Created directory: {}", templatePath.getParent());
            } catch (IOException e) {
                logger.error("Failed to create directory for Excel template: {}", e.getMessage(), e);
            }
        } else {
            logger.info("RMA Excel template found at: {}", templatePath);
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
        
        // Location info in E14
        if (rma.getLocation() != null) {
            setCellValue(sheet, "E14", rma.getLocation().getDisplayName()); // Location information
        }
        
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
        
        // Problem Information fields using updated cell references
        setCellValue(sheet, "B38", rma.getProblemDiscoverer());  // Who discovered - B38
        if (rma.getProblemDiscoveryDate() != null) {
            setCellValue(sheet, "B39", rma.getProblemDiscoveryDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));  // When discovered - B39
        }
        setCellValue(sheet, "B40", rma.getWhatHappened());      // What happened - B40
        setCellValue(sheet, "B44", rma.getWhyAndHowItHappened()); // Why and how - B44
        setCellValue(sheet, "B46", rma.getHowContained());      // How contained - B46
        setCellValue(sheet, "B47", rma.getWhoContained());      // Who contained - B47
        
        // Comments - format the list of comments as a string
        if (rma.getComments() != null && !rma.getComments().isEmpty()) {
            StringBuilder commentsStr = new StringBuilder();
            for (RmaComment comment : rma.getComments()) {
                if (commentsStr.length() > 0) {
                    commentsStr.append("\n");
                }
                if (comment.getUser() != null) {
                    commentsStr.append(comment.getUser().getName()).append(": ");
                }
                commentsStr.append(comment.getContent());
            }
            setCellValue(sheet, "B36", commentsStr.toString());
        }
        
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
            // Get the first sheet
            if (workbook.getNumberOfSheets() == 0) {
                extractedData.put("error", "Excel file contains no sheets");
                return extractedData;
            }
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                extractedData.put("error", "First sheet is null");
                return extractedData;
            }

            // Try to find a sheet named "Data"
            Sheet dataSheet = null;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                if ("Data".equalsIgnoreCase(workbook.getSheetName(i))) {
                    dataSheet = workbook.getSheetAt(i);
                    break;
                }
            }

            // Extract RMA Number from B4
            extractedData.put("rmaNumber", getCellStringValue(sheet, "B4"));
            
            // Extract SAP Notification Number from J4
            extractedData.put("sapNotificationNumber", getCellStringValue(sheet, "J4"));
            
            // Extract Service Order from J5
            extractedData.put("serviceOrder", getCellStringValue(sheet, "J5"));
            
            // Extract Written Date from B6
            String writtenDateStr = getCellStringValue(sheet, "B6");
            if (writtenDateStr != null && !writtenDateStr.isEmpty()) {
                try {
                    // Try to parse the date using common formats
                    LocalDate parsedDate = parseDate(writtenDateStr);
                    if (parsedDate != null) {
                        // Format as ISO date (yyyy-MM-dd) for proper conversion in the controller
                        extractedData.put("writtenDate", parsedDate.format(DateTimeFormatter.ISO_DATE));
                        logger.info("Extracted Written Date from B6: {} (parsed as: {})", 
                                 writtenDateStr, parsedDate.format(DateTimeFormatter.ISO_DATE));
                    } else {
                        logger.warn("Could not parse Written Date from B6: {}", writtenDateStr);
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing Written Date from B6: {}", writtenDateStr, e);
                }
            }
            
            // Extract Customer Information Email from B9
            String customerEmail = getCellStringValue(sheet, "B9");
            if (customerEmail != null && !customerEmail.isEmpty()) {
                extractedData.put("customerEmail", customerEmail);
            }
            
            // Extract exposed to process gas value from A21 (Data sheet)
            if (dataSheet != null) {
                String exposedValue = getCellStringValue(dataSheet, "A21");
                if (exposedValue != null) {
                    boolean exposed = isTrueValue(exposedValue);
                    extractedData.put("exposedToProcessGasOrChemicals", exposed);
                    
                    // If exposed, also extract purged value from A25
                    if (exposed) {
                        String purgedValue = getCellStringValue(dataSheet, "A25");
                        if (purgedValue != null) {
                            extractedData.put("purged", isTrueValue(purgedValue));
                        }
                    }
                }
            }
            
            // Extract Reason for Request from G7
            String reasonForRequest = getCellStringValue(sheet, "G7");
            if (reasonForRequest != null && !reasonForRequest.isEmpty()) {
                extractedData.put("reasonForRequest", reasonForRequest);
            }
            
            // Extract DSS Product Line from G9
            String dssProductLine = getCellStringValue(sheet, "G9");
            if (dssProductLine != null && !dssProductLine.isEmpty()) {
                extractedData.put("dssProductLine", dssProductLine);
            }
            
            // Extract System Description from G11
            String systemDescription = getCellStringValue(sheet, "G11");
            if (systemDescription != null && !systemDescription.isEmpty()) {
                extractedData.put("systemDescription", systemDescription);
            }
            
            // Extract Customer Information
            extractedData.put("customerName", getCellStringValue(sheet, "B11")); // Customer Name
            extractedData.put("companyShipToName", getCellStringValue(sheet, "B12")); // Company Ship To Name
            extractedData.put("companyShipToAddress", getCellStringValue(sheet, "B13")); // Address
            
            // Extract Location information from E14
            String locationInfo = getCellStringValue(sheet, "E14");
            if (locationInfo != null && !locationInfo.isEmpty()) {
                extractedData.put("locationName", locationInfo);
                logger.info("Extracted location information: {}", locationInfo);
            }
            
            // Extract City and State from B14
            String cityState = getCellStringValue(sheet, "B14");
            if (cityState != null && !cityState.isEmpty()) {
                String[] parts = cityState.split(",");
                if (parts.length > 0) {
                    extractedData.put("city", parts[0].trim());
                    
                    if (parts.length > 1) {
                        extractedData.put("state", parts[1].trim());
                    }
                }
            }
            
            // Extract Zip Code from B15
            extractedData.put("zipCode", getCellStringValue(sheet, "B15"));
            
            // Extract Attn from B16
            extractedData.put("attn", getCellStringValue(sheet, "B16"));
            
            // Extract Contact Phone Number from B17
            extractedData.put("customerPhone", getCellStringValue(sheet, "B17"));
            
            // Extract Equipment and Serial Number from F14
            String equipmentInfo = getCellStringValue(sheet, "F14");
            if (equipmentInfo != null && !equipmentInfo.isEmpty()) {
                extractedData.put("equipmentInfo", equipmentInfo);
            }
            
            // Extract Chemical/Gas Service from J18 for tool matching
            String chemicalGasService = getCellStringValue(sheet, "J18");
            if (chemicalGasService != null && !chemicalGasService.isEmpty()) {
                extractedData.put("chemicalGasService", chemicalGasService);
            }
            
            // Extract Downtime from J22
            String downtimeStr = getCellStringValue(sheet, "J22");
            if (downtimeStr != null && !downtimeStr.isEmpty()) {
                try {
                    Double downtime = Double.parseDouble(downtimeStr);
                    extractedData.put("downtimeHours", downtime);
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse downtime value: {}", downtimeStr);
                }
            }
            
            // Extract Parts (up to 4) with the new specified cell addresses
            List<Map<String, Object>> partsList = new ArrayList<>();
            
            // Part 1
            Map<String, Object> part1 = new HashMap<>();
            part1.put("partName", ""); // Leave Part Name blank as requested
            part1.put("partNumber", getCellStringValue(sheet, "B26"));
            part1.put("productDescription", getCellStringValue(sheet, "B27"));
            part1.put("quantity", parseQuantity(getCellStringValue(sheet, "B25")));
            part1.put("replacementRequired", false); // Default
            partsList.add(part1);
            
            // Part 2
            Map<String, Object> part2 = new HashMap<>();
            part2.put("partName", ""); // Leave Part Name blank as requested
            part2.put("partNumber", getCellStringValue(sheet, "I26"));
            part2.put("productDescription", getCellStringValue(sheet, "I27"));
            part2.put("quantity", parseQuantity(getCellStringValue(sheet, "I25")));
            part2.put("replacementRequired", false); // Default
            partsList.add(part2);
            
            // Part 3
            Map<String, Object> part3 = new HashMap<>();
            part3.put("partName", ""); // Leave Part Name blank as requested
            part3.put("partNumber", getCellStringValue(sheet, "B31"));
            part3.put("productDescription", getCellStringValue(sheet, "B32"));
            part3.put("quantity", parseQuantity(getCellStringValue(sheet, "B30")));
            part3.put("replacementRequired", false); // Default
            partsList.add(part3);
            
            // Part 4
            Map<String, Object> part4 = new HashMap<>();
            part4.put("partName", ""); // Leave Part Name blank as requested
            part4.put("partNumber", getCellStringValue(sheet, "I31"));
            part4.put("productDescription", getCellStringValue(sheet, "I32"));
            part4.put("quantity", parseQuantity(getCellStringValue(sheet, "I30")));
            part4.put("replacementRequired", false); // Default
            partsList.add(part4);
            
            // Only include parts that have some data
            List<Map<String, Object>> filteredPartsList = partsList.stream()
                .filter(part -> {
                    String partNumber = (String) part.get("partNumber");
                    String description = (String) part.get("productDescription");
                    return (partNumber != null && !partNumber.isEmpty()) || 
                           (description != null && !description.isEmpty());
                })
                .toList();
            
            if (!filteredPartsList.isEmpty()) {
                extractedData.put("parts", filteredPartsList);
            }
            
            // Extract Instructions for exposed component from F35
            extractedData.put("instructionsForExposedComponent", getCellStringValue(sheet, "F35"));
            
            // Extract Problem Information fields instead of Description - using updated cell references
            extractedData.put("problemDiscoverer", getCellStringValue(sheet, "B38"));      // Who discovered - B38
            String discoveryDate = getCellStringValue(sheet, "B39");                       // When discovered - B39
            if (discoveryDate != null && !discoveryDate.isEmpty()) {
                try {
                    LocalDate parsedDate = parseDate(discoveryDate);
                    if (parsedDate != null) {
                        extractedData.put("problemDiscoveryDate", parsedDate.format(DateTimeFormatter.ISO_DATE));
                    }
                } catch (Exception e) {
                    logger.warn("Could not parse problem discovery date: {}", discoveryDate);
                }
            }
            extractedData.put("whatHappened", getCellStringValue(sheet, "B40"));           // What happened - B40
            extractedData.put("whyAndHowItHappened", getCellStringValue(sheet, "B44"));    // Why and how - B44
            extractedData.put("howContained", getCellStringValue(sheet, "B46"));           // How contained - B46
            extractedData.put("whoContained", getCellStringValue(sheet, "B47"));           // Who contained - B47
            
            // Extract Comments from B36
            extractedData.put("comments", getCellStringValue(sheet, "B36"));
            
        } catch (Exception e) {
            logger.error("Error extracting data from Excel file", e);
            extractedData.put("error", "Failed to extract data: " + e.getMessage());
        }
        
        return extractedData;
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
} 