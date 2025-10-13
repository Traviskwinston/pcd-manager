package com.pcd.manager.service;

import com.pcd.manager.model.Location;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.repository.PassdownRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for importing passdowns from Excel files
 * Handles parsing, tool/technician matching, and preview generation
 */
@Service
public class PassdownExcelImportService {
    
    private static final Logger logger = LoggerFactory.getLogger(PassdownExcelImportService.class);
    
    @Autowired
    private ToolRepository toolRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PassdownRepository passdownRepository;
    
    // Regex pattern for extracting tool name (2-4 letters + 3 numbers, e.g., BT151 from BT151D)
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^([A-Z]{2,4}\\d{3})");
    
    // Regex pattern for matching initials (2-4 uppercase letters)
    private static final Pattern INITIALS_PATTERN = Pattern.compile("^[A-Z]{2,4}$");
    
    /**
     * Step 1: Parse Excel file and extract unique tools and technicians
     * Returns data for the confirmation modal
     */
    public Map<String, Object> parseExcelForReview(MultipartFile file, Location userLocation) throws IOException {
        logger.info("Starting Excel parse for review. File: {}, Location: {}", 
                   file.getOriginalFilename(), userLocation.getDisplayName());
        
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        
        // Sets to track unique items
        Set<String> uniqueToolStrings = new HashSet<>();
        Set<String> uniqueTechInitials = new HashSet<>();
        int totalRows = 0;
        
        // Process all sheets
        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            String sheetName = sheet.getSheetName();
            logger.info("Processing sheet: {} (index: {})", sheetName, sheetIndex);
            
            // Find header row and column indices
            Row headerRow = findHeaderRow(sheet);
            if (headerRow == null) {
                logger.warn("No header row found in sheet: {}", sheetName);
                continue;
            }
            
            logger.info("Header row found at index: {}, Last row num: {}", headerRow.getRowNum(), sheet.getLastRowNum());
            
            Map<String, Integer> columnIndices = mapColumnIndices(headerRow);
            
            // Process data rows
            int rowsProcessedInSheet = 0;
            int rowsSkippedInSheet = 0;
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    rowsSkippedInSheet++;
                    continue;
                }
                if (isRowEmpty(row)) {
                    rowsSkippedInSheet++;
                    continue;
                }
                
                rowsProcessedInSheet++;
                totalRows++;
                
                // Extract tool string
                String toolString = getCellValueAsString(row, columnIndices.get("tool"));
                if (toolString != null && !toolString.trim().isEmpty() 
                    && !toolString.equalsIgnoreCase("Other") && !toolString.equalsIgnoreCase("N/A")) {
                    uniqueToolStrings.add(toolString.trim());
                }
                
                // Extract tech initials
                String techString = getCellValueAsString(row, columnIndices.get("tech"));
                if (techString != null && !techString.trim().isEmpty()) {
                    // Split on multiple delimiters: /, \, ,, &
                    String[] techParts = techString.split("[/\\\\,&]+");
                    for (String part : techParts) {
                        String cleaned = part.trim().toUpperCase();
                        if (INITIALS_PATTERN.matcher(cleaned).matches()) {
                            uniqueTechInitials.add(cleaned);
                        }
                    }
                }
            }
            
            logger.info("Sheet '{}' - Rows processed: {}, Rows skipped: {}", sheetName, rowsProcessedInSheet, rowsSkippedInSheet);
        }
        
        workbook.close();
        
        logger.info("Extraction complete. Total rows: {}, Unique tools: {}, Unique techs: {}", 
                   totalRows, uniqueToolStrings.size(), uniqueTechInitials.size());
        
        // Match tools and techs to database
        List<Map<String, Object>> toolMatches = matchTools(uniqueToolStrings);
        List<Map<String, Object>> techMatches = matchTechnicians(uniqueTechInitials);
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalRows", totalRows);
        result.put("toolMatches", toolMatches);
        result.put("techMatches", techMatches);
        
        return result;
    }
    
    /**
     * Step 2: Generate preview of passdowns to be imported
     * Uses confirmed mappings from user
     */
    public Map<String, Object> generatePreview(MultipartFile file, Location userLocation,
                                                Map<String, Long> toolMappings, // excelToolString -> toolId (or null for "No Tool")
                                                Map<String, Long> techMappings) // initials -> userId (or null for "No Tech")
                                                throws IOException {
        logger.info("Generating preview with confirmed mappings. Location timezone: {}", userLocation.getTimeZone());
        
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Map<String, List<Map<String, Object>>> passdownsByMonth = new LinkedHashMap<>();
        
        // Initialize all 12 months
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        for (String month : months) {
            passdownsByMonth.put(month, new ArrayList<>());
        }
        
        int rowCounter = 0;
        List<Map<String, Object>> allEntries = new ArrayList<>(); // Collect all entries first
        
        // PASS 1: Process all sheets and collect entries
        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            String sheetName = sheet.getSheetName();
            
            logger.info("generatePreview - Processing sheet: {} (index: {})", sheetName, sheetIndex);
            
            Row headerRow = findHeaderRow(sheet);
            if (headerRow == null) {
                logger.warn("generatePreview - No header row found in sheet: {}", sheetName);
                continue;
            }
            
            logger.info("generatePreview - Header row at index: {}, Last row num: {}", headerRow.getRowNum(), sheet.getLastRowNum());
            
            Map<String, Integer> columnIndices = mapColumnIndices(headerRow);
            
            // Process data rows
            int rowsProcessedInSheet = 0;
            int rowsSkippedInSheet = 0;
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    rowsSkippedInSheet++;
                    continue;
                }
                if (isRowEmpty(row)) {
                    rowsSkippedInSheet++;
                    continue;
                }
                
                rowsProcessedInSheet++;
                rowCounter++;
                
                // Extract row data
                String dateString = getCellValueAsString(row, columnIndices.get("date"));
                String toolString = getCellValueAsString(row, columnIndices.get("tool"));
                String taskString = getCellValueAsString(row, columnIndices.get("task"));
                String techString = getCellValueAsString(row, columnIndices.get("tech"));
                
                // Parse date
                LocalDate passdownDate = parseDate(row, columnIndices.get("date"), userLocation.getTimeZone());
                
                // Resolve tools
                List<Long> toolIds = resolveTools(toolString, toolMappings);
                
                // Resolve techs
                List<Long> techIds = resolveTechs(techString, techMappings);
                
                // Create entry
                Map<String, Object> entry = new HashMap<>();
                entry.put("rowId", rowCounter);
                entry.put("sheetName", sheetName);
                entry.put("date", passdownDate); // Might be null
                entry.put("dateString", dateString);
                entry.put("toolIds", toolIds);
                entry.put("toolString", toolString);
                entry.put("task", taskString);
                entry.put("techIds", techIds);
                entry.put("techString", techString);
                
                allEntries.add(entry);
            }
            
            logger.info("generatePreview - Sheet '{}' - Rows processed: {}, Rows skipped: {}", sheetName, rowsProcessedInSheet, rowsSkippedInSheet);
        }
        
        // PASS 2: Infer missing dates from surrounding entries
        inferMissingDates(allEntries);
        
        // PASS 3: Organize into months and flag entries needing attention
        for (Map<String, Object> entry : allEntries) {
            LocalDate passdownDate = (LocalDate) entry.get("date");
            List<Long> toolIds = (List<Long>) entry.get("toolIds");
            List<Long> techIds = (List<Long>) entry.get("techIds");
            String toolString = (String) entry.get("toolString");
            String techString = (String) entry.get("techString");
            
            // Determine if flagged
            boolean flagged = (passdownDate == null) || 
                             (toolIds.isEmpty() && (toolString == null || toolString.trim().isEmpty())) ||
                             (techIds.isEmpty() && (techString == null || techString.trim().isEmpty()));
            entry.put("flagged", flagged);
            
            // Add to appropriate month
            if (passdownDate != null) {
                String monthKey = months[passdownDate.getMonthValue() - 1];
                passdownsByMonth.get(monthKey).add(entry);
            } else {
                // If still no date after inference, add to Jan
                passdownsByMonth.get("Jan").add(entry);
            }
        }
        
        workbook.close();
        
        logger.info("generatePreview - Total passdown entries created: {}", rowCounter);
        
        logger.info("Preview generated. Total entries: {}", rowCounter);
        
        Map<String, Object> result = new HashMap<>();
        result.put("passdownsByMonth", passdownsByMonth);
        result.put("totalEntries", rowCounter);
        
        return result;
    }
    
    /**
     * Step 3: Import passdowns into database
     * Uses final edited preview data from user
     */
    @Transactional
    public Map<String, Object> importPassdowns(List<Map<String, Object>> finalPassdownData, User creator) {
        logger.info("Importing {} passdowns for user: {}", finalPassdownData.size(), creator.getEmail());
        
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        
        for (Map<String, Object> entry : finalPassdownData) {
            try {
                // Parse entry data
                LocalDate date = LocalDate.parse((String) entry.get("date"));
                String task = (String) entry.get("task");
                
                // Convert toolIds (may come as Integer or Long from JSON)
                List<Long> toolIds = new ArrayList<>();
                Object toolIdsObj = entry.get("toolIds");
                if (toolIdsObj instanceof List) {
                    for (Object id : (List<?>) toolIdsObj) {
                        if (id instanceof Number) {
                            toolIds.add(((Number) id).longValue());
                        }
                    }
                }
                
                // Convert techIds (may come as Integer or Long from JSON)
                List<Long> techIds = new ArrayList<>();
                Object techIdsObj = entry.get("techIds");
                if (techIdsObj instanceof List) {
                    for (Object id : (List<?>) techIdsObj) {
                        if (id instanceof Number) {
                            techIds.add(((Number) id).longValue());
                        }
                    }
                }
                
                // Check for duplicates (same date, comment, tools, and technicians)
                // TEMPORARILY DISABLED FOR TESTING
                /*
                if (isDuplicate(date, task, toolIds, techIds)) {
                    logger.info("Skipping duplicate passdown: date={}, task='{}', tools={}, techs={}", date, task, toolIds, techIds);
                    skipped++;
                    continue;
                } else {
                    logger.debug("Importing passdown: date={}, task='{}', tools={}, techs={}", date, task, toolIds, techIds);
                }
                */
                logger.debug("Importing passdown: date={}, task='{}', tools={}, techs={}", date, task, toolIds, techIds);
                
                // Create passdown
                Passdown passdown = new Passdown();
                passdown.setDate(date);
                passdown.setComment(task);
                passdown.setUser(creator);
                passdown.setCreatedDate(LocalDateTime.now());
                
                // Add tools
                if (toolIds != null && !toolIds.isEmpty()) {
                    Set<Tool> tools = new HashSet<>();
                    for (Long toolId : toolIds) {
                        toolRepository.findById(toolId).ifPresent(tools::add);
                    }
                    passdown.setTools(tools);
                } else {
                    passdown.setTools(new HashSet<>());
                }
                
                // Add techs
                if (techIds != null && !techIds.isEmpty()) {
                    Set<User> techs = new HashSet<>();
                    for (Long techId : techIds) {
                        userRepository.findById(techId).ifPresent(techs::add);
                    }
                    passdown.setAssignedTechs(techs);
                } else {
                    passdown.setAssignedTechs(new HashSet<>());
                }
                
                // Save passdown
                passdownRepository.save(passdown);
                imported++;
                
            } catch (Exception e) {
                logger.error("Error importing passdown entry: {}", entry, e);
                errors.add("Row " + entry.get("rowId") + ": " + e.getMessage());
                skipped++;
            }
        }
        
        logger.info("Import complete. Imported: {}, Skipped: {}", imported, skipped);
        
        Map<String, Object> result = new HashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("errors", errors);
        
        return result;
    }
    
    /**
     * Check if a passdown is a duplicate
     * Only mark as duplicate if date, comment, tools AND technicians all match
     */
    private boolean isDuplicate(LocalDate date, String comment, List<Long> toolIds, List<Long> techIds) {
        if (date == null || comment == null) return false;
        
        // Get all passdowns for this date
        List<Passdown> existing = passdownRepository.findByDateWithUserAndToolOrderByDateDesc(date);
        
        // Check for exact match on date, comment, tools, and technicians
        return existing.stream()
            .anyMatch(p -> {
                // Check comment match
                String existingComment = p.getComment() != null ? p.getComment().trim() : "";
                if (!comment.trim().equalsIgnoreCase(existingComment)) {
                    return false;
                }
                
                // Check tools match
                Set<Long> existingToolIds = p.getTools() != null 
                    ? p.getTools().stream().map(t -> t.getId()).collect(java.util.stream.Collectors.toSet())
                    : new HashSet<>();
                Set<Long> newToolIds = toolIds != null 
                    ? new HashSet<>(toolIds) 
                    : new HashSet<>();
                if (!existingToolIds.equals(newToolIds)) {
                    return false;
                }
                
                // Check technicians match
                Set<Long> existingTechIds = p.getAssignedTechs() != null 
                    ? p.getAssignedTechs().stream().map(u -> u.getId()).collect(java.util.stream.Collectors.toSet())
                    : new HashSet<>();
                Set<Long> newTechIds = techIds != null 
                    ? new HashSet<>(techIds) 
                    : new HashSet<>();
                if (!existingTechIds.equals(newTechIds)) {
                    return false;
                }
                
                // All fields match - this is a duplicate
                return true;
            });
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Infer missing dates from surrounding entries
     * Rules:
     * - If first entry: use same month as entry below, same day or earlier
     * - If middle entry: average date from entries above and below (same month)
     * - If last entry: use same month as entry above, same day or later
     */
    private void inferMissingDates(List<Map<String, Object>> entries) {
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> entry = entries.get(i);
            LocalDate date = (LocalDate) entry.get("date");
            
            if (date != null) continue; // Already has a date
            
            logger.info("Inferring date for entry {} (no date provided)", entry.get("rowId"));
            
            // Find previous entry with a date
            LocalDate prevDate = null;
            for (int j = i - 1; j >= 0; j--) {
                LocalDate d = (LocalDate) entries.get(j).get("date");
                if (d != null) {
                    prevDate = d;
                    break;
                }
            }
            
            // Find next entry with a date
            LocalDate nextDate = null;
            for (int j = i + 1; j < entries.size(); j++) {
                LocalDate d = (LocalDate) entries.get(j).get("date");
                if (d != null) {
                    nextDate = d;
                    break;
                }
            }
            
            LocalDate inferredDate = null;
            
            if (prevDate == null && nextDate != null) {
                // First entry: use same month as next, but on an earlier or same day
                inferredDate = nextDate.withDayOfMonth(Math.min(nextDate.getDayOfMonth(), 1));
                logger.info("Inferred date (first entry): {} based on next date: {}", inferredDate, nextDate);
            } else if (prevDate != null && nextDate == null) {
                // Last entry: use same month as previous, but on same or later day
                int lastDayOfMonth = prevDate.lengthOfMonth();
                int inferredDay = Math.min(prevDate.getDayOfMonth() + 1, lastDayOfMonth);
                inferredDate = prevDate.withDayOfMonth(inferredDay);
                logger.info("Inferred date (last entry): {} based on prev date: {}", inferredDate, prevDate);
            } else if (prevDate != null && nextDate != null) {
                // Middle entry: average between prev and next
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(prevDate, nextDate);
                long midpoint = daysBetween / 2;
                inferredDate = prevDate.plusDays(midpoint);
                logger.info("Inferred date (middle entry): {} (avg of {} and {})", inferredDate, prevDate, nextDate);
            } else {
                // No dates found anywhere - use today
                inferredDate = LocalDate.now();
                logger.warn("No surrounding dates found, using today: {}", inferredDate);
            }
            
            // Update entry
            entry.put("date", inferredDate);
            entry.put("dateString", inferredDate.toString());
        }
    }
    
    /**
     * Find the header row (first row with "Date", "Tool", etc.)
     */
    private Row findHeaderRow(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String value = getCellValueAsString(cell);
                if (value != null && (value.equalsIgnoreCase("Date") || value.equalsIgnoreCase("Tool"))) {
                    return row;
                }
            }
        }
        return null;
    }
    
    /**
     * Map column names to indices
     * Supports flexible column names (Date, Tool/Equipment, Task/Issue/Comment, Tech/Technician)
     */
    private Map<String, Integer> mapColumnIndices(Row headerRow) {
        Map<String, Integer> indices = new HashMap<>();
        
        for (Cell cell : headerRow) {
            String header = getCellValueAsString(cell);
            if (header == null) continue;
            
            header = header.trim().toLowerCase();
            
            if (header.contains("date")) {
                indices.put("date", cell.getColumnIndex());
            } else if (header.contains("tool") || header.contains("equipment")) {
                indices.put("tool", cell.getColumnIndex());
            } else if (header.contains("task") || header.contains("issue") || header.contains("comment") || header.contains("description")) {
                indices.put("task", cell.getColumnIndex());
            } else if (header.contains("tech")) {
                indices.put("tech", cell.getColumnIndex());
            }
        }
        
        logger.debug("Column indices mapped: {}", indices);
        return indices;
    }
    
    /**
     * Check if a row is completely empty
     */
    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            String value = getCellValueAsString(cell);
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Get cell value as string (handles all cell types)
     */
    private String getCellValueAsString(Row row, Integer columnIndex) {
        if (columnIndex == null) return null;
        Cell cell = row.getCell(columnIndex);
        return getCellValueAsString(cell);
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
    
    /**
     * Parse date from Excel cell, convert to location timezone
     */
    private LocalDate parseDate(Row row, Integer columnIndex, String timezone) {
        if (columnIndex == null) return null;
        
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return null;
        
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                // Excel date as numeric
                LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
                ZoneId zoneId = ZoneId.of(timezone != null ? timezone : "America/Phoenix");
                return dateTime.atZone(zoneId).toLocalDate();
            } else {
                // Try parsing as string
                String dateString = getCellValueAsString(cell);
                if (dateString != null && !dateString.trim().isEmpty()) {
                    // Try multiple date formats
                    // TODO: Implement flexible date parsing
                    return LocalDate.parse(dateString);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse date from cell: {}", getCellValueAsString(cell), e);
        }
        
        return null;
    }
    
    /**
     * Match tool strings to database tools
     * Returns list of: {excelString, matched: true/false, toolId, toolName}
     */
    private List<Map<String, Object>> matchTools(Set<String> toolStrings) {
        List<Map<String, Object>> matches = new ArrayList<>();
        List<Tool> allTools = toolRepository.findAll();
        
        for (String toolString : toolStrings) {
            // Split on delimiters
            String[] parts = toolString.split("[/\\\\,]+");
            
            for (String part : parts) {
                String cleaned = part.trim().toUpperCase();
                if (cleaned.isEmpty()) continue;
                
                // Extract base part (letters + digits, ignoring trailing letters)
                // e.g., GR151D -> GR151, RFT152 -> RFT152, HG151F -> HG151
                String excelBase = cleaned.replaceAll("[A-Z]+$", "");
                
                logger.debug("Matching tool: '{}' -> base: '{}'", cleaned, excelBase);
                
                // Try to match against tool name or secondary name
                // Match if: exact match OR bases match (both stripped of trailing letters)
                Tool matchedTool = allTools.stream()
                    .filter(t -> {
                        if (t.getName() != null) {
                            String dbName = t.getName().toUpperCase();
                            String dbBase = dbName.replaceAll("[A-Z]+$", "");
                            // Exact match OR base match
                            if (dbName.equals(cleaned) || dbBase.equals(excelBase)) {
                                return true;
                            }
                        }
                        if (t.getSecondaryName() != null) {
                            String dbSecondary = t.getSecondaryName().toUpperCase();
                            String dbSecondaryBase = dbSecondary.replaceAll("[A-Z]+$", "");
                            // Exact match OR base match
                            if (dbSecondary.equals(cleaned) || dbSecondaryBase.equals(excelBase)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .findFirst()
                    .orElse(null);
                
                logger.debug("Match result for '{}': {}", cleaned, matchedTool != null ? matchedTool.getName() : "NO MATCH");
                
                Map<String, Object> match = new HashMap<>();
                match.put("excelString", part.trim());
                match.put("matched", matchedTool != null);
                match.put("toolId", matchedTool != null ? matchedTool.getId() : null);
                match.put("toolName", matchedTool != null ? matchedTool.getName() : null);
                
                matches.add(match);
            }
        }
        
        logger.info("Tool matching complete. Total: {}, Matched: {}", 
                   matches.size(), matches.stream().filter(m -> (Boolean) m.get("matched")).count());
        
        return matches;
    }
    
    /**
     * Match technician initials to database users
     * Returns list of: {initials, matched: true/false, userId, userName}
     */
    private List<Map<String, Object>> matchTechnicians(Set<String> initialsSet) {
        List<Map<String, Object>> matches = new ArrayList<>();
        List<User> allUsers = userRepository.findAll();
        
        logger.debug("Matching technicians. Total users in DB: {}", allUsers.size());
        for (User u : allUsers) {
            String extracted = extractInitials(u.getName());
            logger.debug("User: '{}' -> initials: '{}'", u.getName(), extracted);
        }
        
        for (String initials : initialsSet) {
            logger.debug("Looking for initials: '{}'", initials);
            
            // Try to match by parsing user names
            User matchedUser = allUsers.stream()
                .filter(u -> {
                    if (u.getName() == null) return false;
                    String extracted = extractInitials(u.getName());
                    boolean match = extracted.equalsIgnoreCase(initials);
                    logger.debug("  Checking '{}' (initials: '{}') -> match: {}", u.getName(), extracted, match);
                    return match;
                })
                .findFirst()
                .orElse(null);
            
            logger.debug("Final match for '{}': {}", initials, matchedUser != null ? matchedUser.getName() : "NO MATCH");
            
            Map<String, Object> match = new HashMap<>();
            match.put("initials", initials);
            match.put("matched", matchedUser != null);
            match.put("userId", matchedUser != null ? matchedUser.getId() : null);
            match.put("userName", matchedUser != null ? matchedUser.getName() : null);
            
            matches.add(match);
        }
        
        logger.info("Tech matching complete. Total: {}, Matched: {}", 
                   matches.size(), matches.stream().filter(m -> (Boolean) m.get("matched")).count());
        
        return matches;
    }
    
    /**
     * Extract initials from full name (e.g., "Travis Winston" -> "TW")
     */
    private String extractInitials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "";
        
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(part.substring(0, 1).toUpperCase());
            }
        }
        
        return initials.toString();
    }
    
    /**
     * Resolve tool string to list of tool IDs using confirmed mappings
     */
    private List<Long> resolveTools(String toolString, Map<String, Long> mappings) {
        if (toolString == null || toolString.trim().isEmpty()) return new ArrayList<>();
        
        List<Long> toolIds = new ArrayList<>();
        String[] parts = toolString.split("[/\\\\,]+");
        
        for (String part : parts) {
            String cleaned = part.trim();
            if (mappings.containsKey(cleaned) && mappings.get(cleaned) != null) {
                toolIds.add(mappings.get(cleaned));
            }
        }
        
        return toolIds;
    }
    
    /**
     * Resolve tech string to list of user IDs using confirmed mappings
     */
    private List<Long> resolveTechs(String techString, Map<String, Long> mappings) {
        if (techString == null || techString.trim().isEmpty()) return new ArrayList<>();
        
        List<Long> techIds = new ArrayList<>();
        String[] parts = techString.split("[/\\\\,&]+");
        
        for (String part : parts) {
            String cleaned = part.trim().toUpperCase();
            if (mappings.containsKey(cleaned) && mappings.get(cleaned) != null) {
                techIds.add(mappings.get(cleaned));
            }
        }
        
        return techIds;
    }
}

