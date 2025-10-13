package com.pcd.manager.service;

import com.pcd.manager.model.Tool;
import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaDocument;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.MovingPart;
import com.pcd.manager.model.ToolComment;
import com.pcd.manager.model.User;
import com.pcd.manager.model.Location;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.repository.RmaDocumentRepository;
import com.pcd.manager.repository.RmaPictureRepository;
import com.pcd.manager.repository.MovingPartRepository;
import com.pcd.manager.repository.ToolCommentRepository;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.repository.LocationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.io.InputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class ToolService {

    private static final Logger logger = LoggerFactory.getLogger(ToolService.class);

    private final ToolRepository toolRepository;
    private final RmaRepository rmaRepository;
    private final RmaDocumentRepository documentRepository;
    private final RmaPictureRepository pictureRepository;
    private final MovingPartRepository movingPartRepository;
    private final ToolCommentRepository toolCommentRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private PassdownService passdownService; // Not final anymore, will be set by setter

    @Autowired
    public ToolService(ToolRepository toolRepository, 
                      RmaRepository rmaRepository,
                      RmaDocumentRepository documentRepository,
                      RmaPictureRepository pictureRepository,
                      MovingPartRepository movingPartRepository,
                      ToolCommentRepository toolCommentRepository,
                      UserRepository userRepository,
                      LocationRepository locationRepository) {
        this.toolRepository = toolRepository;
        this.rmaRepository = rmaRepository;
        this.documentRepository = documentRepository;
        this.pictureRepository = pictureRepository;
        this.movingPartRepository = movingPartRepository;
        this.toolCommentRepository = toolCommentRepository;
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        // PassdownService will be injected via setter
    }
    
    // Setter method for PassdownService
    @Autowired
    public void setPassdownService(PassdownService passdownService) {
        this.passdownService = passdownService;
    }

    @Cacheable(value = "tools-list", key = "'all-tools'")
    public List<Tool> getAllTools() {
        logger.info("Fetching all tools (cacheable)");
        return toolRepository.findAll();
    }
    
    /**
     * Get lightweight tools for dropdowns/selects - only loads essential fields
     */
    @Cacheable(value = "dropdown-data", key = "'tools-dropdown'")
    public List<Tool> getAllToolsForDropdown() {
        logger.info("Fetching tools for dropdown (cacheable)");
        return toolRepository.findAllForListView();
    }

    /**
     * Get all tools with technicians eagerly loaded
     */
    public List<Tool> getAllToolsWithTechnicians() {
        return toolRepository.findAllWithTechnicians();
    }

    @Cacheable(value = "tool-details", key = "#id")
    public Optional<Tool> getToolById(Long id) {
        logger.debug("Fetching tool by ID: {} (cacheable)", id);
        return toolRepository.findById(id);
    }

    @CacheEvict(value = {"tools-list", "dropdown-data", "tool-details"}, allEntries = true)
    public Tool saveTool(Tool tool) {
        logger.info("Saving tool and evicting caches");
        return toolRepository.save(tool);
    }

    /**
     * Clear a checklist date on a collection of tools by mapping function name
     */
    @Transactional
    public void clearChecklistDateForTools(List<Tool> tools, String getterName) {
        if (tools == null || tools.isEmpty() || getterName == null) return;
        String setterName = getterName.replaceFirst("get", "set");
        try {
            for (Tool t : tools) {
                try {
                    java.lang.reflect.Method setter = Tool.class.getMethod(setterName, java.time.LocalDate.class);
                    setter.invoke(t, new Object[]{null});
                    toolRepository.save(t);
                } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            logger.warn("Error clearing checklist date using {}: {}", getterName, e.getMessage());
        }
    }

    @CacheEvict(value = {"tools-list", "dropdown-data", "tool-details"}, allEntries = true)
    public void deleteTool(Long id) {
        logger.info("Deleting tool {} and evicting caches", id);
        // Detach or cleanup dependent rows that could block deletion in Postgres
        try {
            // 1) Moving parts where this tool is the source (fromTool) or in destination chain
            List<MovingPart> related = movingPartRepository.findAllByToolId(id);
            if (related != null && !related.isEmpty()) {
                logger.info("Detaching {} moving part records from Tool {}", related.size(), id);
                for (MovingPart mp : related) {
                    if (mp.getFromTool() != null && id.equals(mp.getFromTool().getId())) {
                        mp.setFromTool(null);
                    }
                    // destinationToolIds is likely an embedded list; clear occurrences of this tool id
                    if (mp.getDestinationToolIds() != null && !mp.getDestinationToolIds().isEmpty()) {
                        mp.getDestinationToolIds().removeIf(tid -> tid != null && tid.equals(id));
                    }
                }
                movingPartRepository.saveAll(related);
            }
        } catch (Exception e) {
            logger.warn("Error detaching moving parts for Tool {}: {}", id, e.getMessage());
        }

        toolRepository.deleteById(id);
    }

    public Optional<Tool> findToolBySerialNumber(String serialNumber) {
        if (serialNumber == null || serialNumber.isEmpty()) {
            return Optional.empty();
        }
        // This method will try to find a tool by its serialNumber1 field.
        // The RmaController can call this for both parsedSerial1 and parsedSerial2 if needed.
        return toolRepository.findBySerialNumber1(serialNumber);
    }

    public Optional<Tool> findToolBySerialNumber2(String serialNumber2) {
        return toolRepository.findBySerialNumber2(serialNumber2);
    }

    /**
     * Link a document from a tool to an RMA
     *
     * @param filePath the path of the document file
     * @param fileName the original name of the document
     * @param rmaId the ID of the RMA to link to
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean linkDocumentToRma(String filePath, String fileName, Long rmaId) {
        logger.info("Linking document {} from tool to RMA {}", filePath, rmaId);
        
        try {
            // Find the RMA
            Optional<Rma> rmaOpt = rmaRepository.findById(rmaId);
            if (rmaOpt.isEmpty()) {
                logger.warn("RMA with ID {} not found", rmaId);
                return false;
            }
            
            Rma rma = rmaOpt.get();
            
            // Create a new RMA document
            RmaDocument document = new RmaDocument();
            document.setRma(rma);
            document.setFilePath(filePath);
            document.setFileName(fileName != null ? fileName : "Document-" + UUID.randomUUID().toString());
            document.setFileType(getFileTypeFromPath(filePath));
            document.setFileSize(0L); // We don't have this info, but the field is required
            
            // Save the document
            documentRepository.save(document);
            logger.info("Successfully linked document to RMA {}", rmaId);
            
            return true;
        } catch (Exception e) {
            logger.error("Error linking document to RMA: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Link a picture from a tool to an RMA
     *
     * @param filePath the path of the picture file
     * @param fileName the original name of the picture
     * @param rmaId the ID of the RMA to link to
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean linkPictureToRma(String filePath, String fileName, Long rmaId) {
        logger.info("Linking picture {} from tool to RMA {}", filePath, rmaId);
        
        try {
            // Find the RMA
            Optional<Rma> rmaOpt = rmaRepository.findById(rmaId);
            if (rmaOpt.isEmpty()) {
                logger.warn("RMA with ID {} not found", rmaId);
                return false;
            }
            
            Rma rma = rmaOpt.get();
            
            // Create a new RMA picture
            RmaPicture picture = new RmaPicture();
            picture.setRma(rma);
            picture.setFilePath(filePath);
            picture.setFileName(fileName != null ? fileName : "Picture-" + UUID.randomUUID().toString());
            picture.setFileType(getFileTypeFromPath(filePath));
            picture.setFileSize(0L); // We don't have this info, but the field is required
            
            // Save the picture
            pictureRepository.save(picture);
            logger.info("Successfully linked picture to RMA {}", rmaId);
            
            return true;
        } catch (Exception e) {
            logger.error("Error linking picture to RMA: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Link a document from a tool to a passdown
     *
     * @param filePath the path of the document file
     * @param fileName the original name of the document
     * @param passdownId the ID of the passdown to link to
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean linkDocumentToPassdown(String filePath, String fileName, Long passdownId) {
        logger.info("Linking document {} from tool to passdown {}", filePath, passdownId);
        
        try {
            // Find the passdown
            Optional<Passdown> passdownOpt = passdownService.getPassdownById(passdownId);
            if (passdownOpt.isEmpty()) {
                logger.warn("Passdown with ID {} not found", passdownId);
                return false;
            }
            
            Passdown passdown = passdownOpt.get();
            
            // Add the document to the passdown
            passdown.getDocumentPaths().add(filePath);
            if (fileName != null) {
                passdown.getDocumentNames().put(filePath, fileName);
            }
            
            // Save the passdown
            passdownService.savePassdown(passdown);
            logger.info("Successfully linked document to passdown {}", passdownId);
            
            return true;
        } catch (Exception e) {
            logger.error("Error linking document to passdown: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Link a picture from a tool to a passdown
     *
     * @param filePath the path of the picture file
     * @param fileName the original name of the picture
     * @param passdownId the ID of the passdown to link to
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean linkPictureToPassdown(String filePath, String fileName, Long passdownId) {
        logger.info("Linking picture {} from tool to passdown {}", filePath, passdownId);
        
        try {
            // Find the passdown
            Optional<Passdown> passdownOpt = passdownService.getPassdownById(passdownId);
            if (passdownOpt.isEmpty()) {
                logger.warn("Passdown with ID {} not found", passdownId);
                return false;
            }
            
            Passdown passdown = passdownOpt.get();
            
            // Add the picture to the passdown
            passdown.getPicturePaths().add(filePath);
            if (fileName != null) {
                passdown.getPictureNames().put(filePath, fileName);
            }
            
            // Save the passdown
            passdownService.savePassdown(passdown);
            logger.info("Successfully linked picture to passdown {}", passdownId);
            
            return true;
        } catch (Exception e) {
            logger.error("Error linking picture to passdown: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Extract file type from a file path
     *
     * @param filePath the path of the file
     * @return the file type (extension)
     */
    private String getFileTypeFromPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "unknown";
        }
        
        int lastDotIndex = filePath.lastIndexOf(".");
        if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex + 1).toLowerCase();
        }
        
        return "unknown";
    }

    @Transactional
    public boolean linkFileToTool(Long toolId, String filePath, String originalFileName, String fileType, String sourceEntityType, Long sourceEntityId) {
        logger.info("Attempting to link file {} (type: {}) to tool ID: {}. Source: {} {}", filePath, fileType, toolId, sourceEntityType, sourceEntityId);

        Optional<Tool> toolOpt = toolRepository.findById(toolId);
        if (toolOpt.isEmpty()) {
            logger.warn("Tool not found with ID: {}", toolId);
            return false;
        }
        Tool tool = toolOpt.get();

        if (filePath == null || filePath.isBlank()) {
            logger.warn("File path is null or blank, cannot link to tool {}", toolId);
            return false;
        }

        if ("document".equalsIgnoreCase(fileType)) {
            if (tool.getDocumentPaths().contains(filePath)) {
                logger.info("Document {} already linked to tool {}. Ensuring name is updated.", filePath, toolId);
                tool.getDocumentNames().put(filePath, originalFileName); // Update name just in case
            } else {
                tool.getDocumentPaths().add(filePath);
                tool.getDocumentNames().put(filePath, originalFileName);
                logger.info("Linked document {} to tool {}", filePath, toolId);
            }
        } else if ("picture".equalsIgnoreCase(fileType)) {
            if (tool.getPicturePaths().contains(filePath)) {
                logger.info("Picture {} already linked to tool {}. Ensuring name is updated.", filePath, toolId);
                tool.getPictureNames().put(filePath, originalFileName); // Update name just in case
            } else {
                tool.getPicturePaths().add(filePath);
                tool.getPictureNames().put(filePath, originalFileName);
                logger.info("Linked picture {} to tool {}", filePath, toolId);
            }
        } else {
            logger.warn("Unsupported file type '{}' for linking to tool.", fileType);
            return false; // Or handle as a generic document
        }

        try {
            toolRepository.save(tool);
            return true;
        } catch (Exception e) {
            logger.error("Error saving tool {} after attempting to link file {}: {}", toolId, filePath, e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public boolean linkMovingPartToTool(Long movingPartId, Long targetToolId) {
        logger.info("Attempting to link MovingPart ID: {} to Tool ID: {}", movingPartId, targetToolId);
        Optional<MovingPart> movingPartOpt = movingPartRepository.findById(movingPartId);
        Optional<Tool> targetToolOpt = toolRepository.findById(targetToolId);

        if (movingPartOpt.isPresent() && targetToolOpt.isPresent()) {
            MovingPart movingPart = movingPartOpt.get();
            Tool targetTool = targetToolOpt.get();

            // Prevent linking to its own fromTool or toTool if that logic is desired
            // For now, allow linking to any tool via this new field.
            movingPart.setAdditionallyLinkedTool(targetTool);
            movingPartRepository.save(movingPart);
            logger.info("Successfully linked MovingPart {} to Tool {}", movingPartId, targetToolId);
            return true;
        }
        if (movingPartOpt.isEmpty()) logger.warn("MovingPart not found with ID: {}", movingPartId);
        if (targetToolOpt.isEmpty()) logger.warn("Target Tool not found with ID: {}", targetToolId);
        return false;
    }

    /**
     * Add a comment to a tool
     */
    @Transactional
    public ToolComment addComment(Long toolId, String content, String userEmail) {
        Tool tool = toolRepository.findById(toolId)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + toolId));
        
        User user = userRepository.findByEmailIgnoreCase(userEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));
        
        ToolComment comment = new ToolComment();
        comment.setContent(content);
        comment.setTool(tool);
        comment.setUser(user);
        comment.setCreatedDate(LocalDateTime.now());
        
        ToolComment savedComment = toolCommentRepository.save(comment);
        
        // Add the comment to the tool's comment list
        tool.getComments().add(savedComment);
        toolRepository.save(tool);
        
        return savedComment;
    }
    
    /**
     * Get all comments for a tool
     */
    public List<ToolComment> getCommentsForTool(Long toolId) {
        return toolCommentRepository.findByToolIdOrderByCreatedDateDesc(toolId);
    }
    
    /**
     * Assign a user to a tool with proper session management
     */
    @Transactional
    @CacheEvict(value = {"tools-list", "dropdown-data", "tool-details"}, allEntries = true)
    public boolean assignUserToTool(Long toolId, String userEmail) {
        try {
            // Load tool with technicians eagerly loaded to avoid lazy loading issues
            Optional<Tool> toolOpt = toolRepository.findByIdWithTechnicians(toolId);
            Optional<User> userOpt = userRepository.findByEmailIgnoreCase(userEmail);
            
            if (toolOpt.isEmpty() || userOpt.isEmpty()) {
                logger.warn("Failed to assign user to tool. Tool found: {}, User found: {}", 
                           toolOpt.isPresent(), userOpt.isPresent());
                return false;
            }
            
            Tool tool = toolOpt.get();
            User user = userOpt.get();
            
            // Set this tool as the user's active tool
            user.setActiveTool(tool);
            userRepository.save(user);
            
            // Add the user to the tool's technician list for dashboard tracking
            // Initialize the collection if it's null
            if (tool.getCurrentTechnicians() == null) {
                tool.setCurrentTechnicians(new java.util.HashSet<>());
            }
            
            // Add the user if not already present
            if (!tool.getCurrentTechnicians().contains(user)) {
                tool.getCurrentTechnicians().add(user);
                toolRepository.save(tool);
            }
            
            logger.info("Successfully assigned tool {} to user {}", tool.getName(), user.getName());
            return true;
        } catch (Exception e) {
            logger.error("Error assigning user to tool: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Unassign a user from a tool with proper session management
     */
    @Transactional
    @CacheEvict(value = {"tools-list", "dropdown-data", "tool-details"}, allEntries = true)
    public boolean unassignUserFromTool(Long toolId, String userEmail) {
        try {
            // Load tool with technicians eagerly loaded to avoid lazy loading issues
            Optional<Tool> toolOpt = toolRepository.findByIdWithTechnicians(toolId);
            Optional<User> userOpt = userRepository.findByEmailIgnoreCase(userEmail);
            
            if (toolOpt.isEmpty() || userOpt.isEmpty()) {
                logger.warn("Failed to unassign user from tool. Tool found: {}, User found: {}", 
                           toolOpt.isPresent(), userOpt.isPresent());
                return false;
            }
            
            Tool tool = toolOpt.get();
            User user = userOpt.get();
            
            // Clear this tool as the user's active tool if it matches
            if (user.getActiveTool() != null && user.getActiveTool().getId().equals(toolId)) {
                user.setActiveTool(null);
                userRepository.save(user);
            }
            
            // Remove the user from the tool's technician list
            if (tool.getCurrentTechnicians() != null && tool.getCurrentTechnicians().contains(user)) {
                tool.getCurrentTechnicians().remove(user);
                toolRepository.save(tool);
            }
            
            logger.info("Successfully unassigned tool {} from user {}", tool.getName(), user.getName());
            return true;
        } catch (Exception e) {
            logger.error("Error unassigning user from tool: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Bulk load comments for multiple tools (optimization for list views)
     */
    public List<ToolComment> getCommentsByToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new ArrayList<>();
        }
        return toolCommentRepository.findByToolIdInOrderByCreatedDateDesc(toolIds);
    }

    /**
     * OPTIMIZATION: Bulk gets lightweight comment data for multiple tools to avoid loading full objects
     * Returns only essential fields: id, createdDate, userName, content, toolId
     * @param toolIds The list of tool IDs
     * @return List of Object arrays with lightweight comment data
     */
    public List<Object[]> findCommentListDataByToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        logger.debug("Bulk getting lightweight comment data for {} tool IDs", toolIds.size());
        List<Object[]> commentData = toolCommentRepository.findCommentListDataByToolIds(toolIds);
        logger.debug("Found {} lightweight comment records for {} tool IDs", commentData.size(), toolIds.size());
        return commentData;
    }
    
    /**
     * Edit a comment
     */
    @Transactional
    @CacheEvict(value = {"tools-list", "dropdown-data", "tool-details"}, allEntries = true)
    public ToolComment editComment(Long commentId, String content, String userEmail) {
        ToolComment comment = toolCommentRepository.findById(commentId)
            .orElseThrow(() -> new IllegalArgumentException("Comment not found: " + commentId));
        
        User user = userRepository.findByEmailIgnoreCase(userEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));
        
        // Basic permission check - only the author can edit their comment
        if (!comment.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You can only edit your own comments");
        }
        
        comment.setContent(content);
        comment.setCreatedDate(LocalDateTime.now()); // Update timestamp
        
        return toolCommentRepository.save(comment);
    }
    
    /**
     * Delete a comment
     */
    @Transactional
    @CacheEvict(value = {"tools-list", "dropdown-data", "tool-details"}, allEntries = true)
    public void deleteComment(Long commentId, String userEmail) {
        ToolComment comment = toolCommentRepository.findById(commentId)
            .orElseThrow(() -> new IllegalArgumentException("Comment not found: " + commentId));
        
        User user = userRepository.findByEmailIgnoreCase(userEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));
        
        // Basic permission check - only the author can delete their comment
        if (!comment.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You can only delete your own comments");
        }
        
        // Remove from tool's comment list
        Tool tool = comment.getTool();
        if (tool != null) {
            tool.getComments().remove(comment);
            toolRepository.save(tool);
        }
        
        toolCommentRepository.deleteById(commentId);
    }

    @Transactional
    @CacheEvict(value = {"tools-list", "dashboard-data"}, allEntries = true)  
    public Map<String, Object> analyzeExcelForDuplicates(MultipartFile file) throws Exception {
        logger.info("Starting Excel analysis for duplicates");
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> duplicates = new ArrayList<>();
        List<Map<String, Object>> validRows = new ArrayList<>();
        
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel file is empty or corrupted");
            }
            
            // Create flexible header mapping (same as createToolsFromExcel)
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel file must have a header row");
            }
            
            Map<String, Integer> headerMap = new HashMap<>();
            int lastColumnNum = headerRow.getLastCellNum();
            
            // Track serial number columns for proper mapping
            List<Integer> serialColumns = new ArrayList<>();
            
            for (int i = 0; i < lastColumnNum; i++) {
                Cell cell = headerRow.getCell(i);
                if (cell == null) continue;
                
                String headerValue = cell.getStringCellValue().trim().toLowerCase();
                if (headerValue.isEmpty()) continue;
                
                logger.debug("Processing header at column {}: '{}'", i, headerValue);
                
                // Flexible header matching
                if (matchesPattern(headerValue, "system")) {
                    headerMap.put("system", i);
                } else if (matchesPattern(headerValue, "equipment location", "equipmentlocation", "tool location", "toollocation", "equipment/location", "equipment / location", "equipment/ location", "equipment /location")) {
                    headerMap.put("equipmentLocation", i);
                } else if (matchesPattern(headerValue, "config", "config#", "config number")) {
                    headerMap.put("config", i);
                } else if (matchesPattern(headerValue, "equipment set", "equipmentset", "set")) {
                    headerMap.put("equipmentSet", i);
                } else if (matchesPattern(headerValue, "tool name", "toolname", "name") && !headerMap.containsKey("toolName")) {
                    headerMap.put("toolName", i);
                } else if (matchesPattern(headerValue, "tool name 2", "toolname2", "name 2", "secondary name") && !headerMap.containsKey("toolName2")) {
                    headerMap.put("toolName2", i);
                } else if (matchesPattern(headerValue, "type", "tool type", "tooltype")) {
                    headerMap.put("type", i);
                } else if (matchesPattern(headerValue, "model", "model number", "model#")) {
                    if (!headerMap.containsKey("model1")) {
                        headerMap.put("model1", i);
                    } else if (!headerMap.containsKey("model2")) {
                        headerMap.put("model2", i);
                    }
                } else if (matchesPattern(headerValue, "serial", "serial number", "serial#")) {
                    serialColumns.add(i);
                } else if (matchesPattern(headerValue, "location")) {
                    headerMap.put("location", i);
                }
            }
            
            // Map serial columns (first = serial1, second = serial2)
            if (serialColumns.size() >= 1) {
                headerMap.put("serial1", serialColumns.get(0));
            }
            if (serialColumns.size() >= 2) {
                headerMap.put("serial2", serialColumns.get(1));
            }
            
            logger.info("Flexible header mapping completed for analysis. Found headers: {}", headerMap.keySet());
            
            int rowNum = 1; // Start after header row
            String lastSystemName = null; // Track system name for inheritance
            
            while (rowNum <= sheet.getLastRowNum()) {
                Row row = sheet.getRow(rowNum);
                
                if (row == null) {
                    rowNum++;
                    continue;
                }
                
                try {
                    // Extract data from row using flexible header mapping
                    String toolName = getCellValueFromMapping(row, headerMap, "toolName");
                    String toolName2 = getCellValueFromMapping(row, headerMap, "toolName2");
                    String type = getCellValueFromMapping(row, headerMap, "type");
                    String modelNumber1 = getCellValueFromMapping(row, headerMap, "model1");
                    String modelNumber2 = getCellValueFromMapping(row, headerMap, "model2");
                    String serialNumber1 = getCellValueFromMapping(row, headerMap, "serial1");
                    String serialNumber2 = getCellValueFromMapping(row, headerMap, "serial2");
                    String locationName = getCellValueFromMapping(row, headerMap, "location");
                    
                    // GasGuard-specific fields
                    String systemName = getCellValueFromMapping(row, headerMap, "system");
                    String equipmentLocation = getCellValueFromMapping(row, headerMap, "equipmentLocation");
                    String configNumber = getCellValueFromMapping(row, headerMap, "config");
                    String equipmentSetStr = getCellValueFromMapping(row, headerMap, "equipmentSet");
                    
                    // System name inheritance: if current system is empty but previous row had a system, inherit it
                    if ((systemName == null || systemName.trim().isEmpty()) && lastSystemName != null) {
                        systemName = lastSystemName;
                        logger.debug("Row {}: Inherited system name '{}' from previous row", rowNum + 1, systemName);
                    } else if (systemName != null && !systemName.trim().isEmpty()) {
                        lastSystemName = systemName.trim(); // Update last system name for future inheritance
                    }
                    
                    // Auto-detect GasGuard tools (have Equipment Location, System is optional)
                    boolean isGasGuard = (equipmentLocation != null && !equipmentLocation.trim().isEmpty());
                    
                    // Skip rows where primary identifier is empty
                    String primaryName;
                    if (isGasGuard) {
                        // For GasGuard, use systemName if available, otherwise equipmentLocation
                        primaryName = (systemName != null && !systemName.trim().isEmpty()) ? 
                                     systemName : equipmentLocation;
                    } else {
                        primaryName = toolName;
                    }
                    
                    if (primaryName == null || primaryName.trim().isEmpty()) {
                        logger.debug("Skipping row {} - no primary identifier", rowNum + 1);
                        rowNum++;
                        continue;
                    }
                    
                    // Create row data map
                    Map<String, Object> rowData = new HashMap<>();
                    rowData.put("rowNumber", rowNum + 1);
                    rowData.put("isGasGuard", isGasGuard);
                    rowData.put("primaryName", primaryName.trim());
                    
                    // Standard tool fields
                    rowData.put("toolName", toolName != null ? toolName.trim() : "");
                    rowData.put("toolName2", toolName2 != null ? toolName2.trim() : "");
                    rowData.put("type", type != null ? type.trim() : "");
                    rowData.put("modelNumber1", modelNumber1 != null ? modelNumber1.trim() : "");
                    rowData.put("modelNumber2", modelNumber2 != null ? modelNumber2.trim() : "");
                    rowData.put("serialNumber1", serialNumber1 != null ? serialNumber1.trim() : "");
                    rowData.put("serialNumber2", serialNumber2 != null ? serialNumber2.trim() : "");
                    rowData.put("locationName", locationName != null ? locationName.trim() : "");
                    
                    // GasGuard-specific fields
                    rowData.put("systemName", systemName != null ? systemName.trim() : "");
                    rowData.put("equipmentLocation", equipmentLocation != null ? equipmentLocation.trim() : "");
                    rowData.put("configNumber", configNumber != null ? configNumber.trim() : "");
                    rowData.put("equipmentSet", equipmentSetStr != null ? equipmentSetStr.trim() : "");
                    
                    // Check for duplicates using enhanced logic
                    Tool existingTool = findPotentialDuplicate(rowData);
                    
                    if (existingTool != null) {
                        // Found a potential duplicate
                        Map<String, Object> duplicateInfo = createDuplicateComparisonData(existingTool, rowData);
                        duplicates.add(duplicateInfo);
                        logger.info("Found potential duplicate for row {}: existing tool ID {}", rowNum + 1, existingTool.getId());
                    } else {
                        // No duplicate found - this is a valid new tool
                        validRows.add(rowData);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error analyzing row {}: {}", rowNum + 1, e.getMessage());
                    // Continue processing other rows
                }
                
                rowNum++;
            }
            
            result.put("duplicates", duplicates);
            result.put("validRows", validRows);
            result.put("totalRows", rowNum - 1);
            result.put("duplicateCount", duplicates.size());
            result.put("validCount", validRows.size());
            
            logger.info("Excel analysis completed. {} duplicates found, {} valid new tools", 
                       duplicates.size(), validRows.size());
            return result;
            
        } catch (IOException e) {
            logger.error("Error reading Excel file", e);
            throw new IllegalArgumentException("Error reading Excel file: " + e.getMessage());
        }
    }

    @Transactional
    @CacheEvict(value = {"tools-list", "dashboard-data"}, allEntries = true)  
    public int createToolsFromExcel(MultipartFile file) throws Exception {
                    logger.info("Starting Excel tool creation process");
        
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel file is empty or corrupted");
            }
            
            // Validate headers
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel file must have a header row");
            }
            
            // Create flexible header mapping
            Map<String, Integer> headerMap = new HashMap<>();
            int lastColumnNum = headerRow.getLastCellNum();
            
            // Track serial number columns for proper mapping
            List<Integer> serialColumns = new ArrayList<>();
            
            for (int i = 0; i < lastColumnNum; i++) {
                Cell cell = headerRow.getCell(i);
                if (cell == null) continue;
                
                String headerValue = cell.getStringCellValue().trim().toLowerCase();
                if (headerValue.isEmpty()) continue;
                
                logger.debug("Processing header at column {}: '{}'", i, headerValue);
                
                // Flexible header matching
                if (matchesPattern(headerValue, "system")) {
                    headerMap.put("system", i);
                } else if (matchesPattern(headerValue, "equipment location", "equipmentlocation", "tool location", "toollocation", "equipment/location", "equipment / location", "equipment/ location", "equipment /location")) {
                    headerMap.put("equipmentLocation", i);
                } else if (matchesPattern(headerValue, "config", "config#", "config number")) {
                    headerMap.put("config", i);
                } else if (matchesPattern(headerValue, "equipment set", "equipmentset", "set")) {
                    headerMap.put("equipmentSet", i);
                } else if (matchesPattern(headerValue, "tool name", "toolname", "name") && !headerMap.containsKey("toolName")) {
                    headerMap.put("toolName", i);
                } else if (matchesPattern(headerValue, "tool name 2", "toolname2", "name 2", "secondary name") && !headerMap.containsKey("toolName2")) {
                    headerMap.put("toolName2", i);
                } else if (matchesPattern(headerValue, "type", "tool type", "tooltype")) {
                    headerMap.put("type", i);
                } else if (matchesPattern(headerValue, "model", "model number", "model#")) {
                    if (!headerMap.containsKey("model1")) {
                        headerMap.put("model1", i);
                    } else if (!headerMap.containsKey("model2")) {
                        headerMap.put("model2", i);
                    }
                } else if (matchesPattern(headerValue, "serial", "serial number", "serial#")) {
                    serialColumns.add(i);
                } else if (matchesPattern(headerValue, "location")) {
                    headerMap.put("location", i);
                }
            }
            
            // Map serial columns (first = serial1, second = serial2)
            if (serialColumns.size() >= 1) {
                headerMap.put("serial1", serialColumns.get(0));
            }
            if (serialColumns.size() >= 2) {
                headerMap.put("serial2", serialColumns.get(1));
            }
            
            logger.info("Flexible header mapping completed. Found headers: {}", headerMap.keySet());
            
            // Resolve current user's active location (fallbacks: user's default, then system default)
            Optional<Location> defaultLocationOpt = Optional.empty();
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getName() != null) {
                    Optional<User> userOpt = userRepository.findByEmailIgnoreCase(auth.getName());
                    if (userOpt.isPresent()) {
                        User current = userOpt.get();
                        if (current.getActiveSite() != null) {
                            defaultLocationOpt = Optional.of(current.getActiveSite());
                        } else if (current.getDefaultLocation() != null) {
                            defaultLocationOpt = Optional.of(current.getDefaultLocation());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not resolve current user for active location: {}", e.getMessage());
            }
            if (!defaultLocationOpt.isPresent()) {
                defaultLocationOpt = locationRepository.findByDefaultLocationIsTrue();
                if (!defaultLocationOpt.isPresent()) {
                    logger.warn("No default location found. Tools will be created without location assignment.");
                }
            }
            
            int toolsCreated = 0;
            int rowNum = 1; // Start after header row
            String lastSystemName = null; // Track system name for inheritance
            LocalDateTime baseUploadTime = LocalDateTime.now(); // Base time for upload dates
            
            while (rowNum <= sheet.getLastRowNum()) {
                Row row = sheet.getRow(rowNum);
                
                if (row == null) {
                    rowNum++;
                    continue;
                }
                
                try {
                    // Extract data from row using flexible header mapping
                    String toolName = getCellValueFromMapping(row, headerMap, "toolName");
                    String toolName2 = getCellValueFromMapping(row, headerMap, "toolName2");
                    String type = getCellValueFromMapping(row, headerMap, "type");
                    String modelNumber1 = getCellValueFromMapping(row, headerMap, "model1");
                    String modelNumber2 = getCellValueFromMapping(row, headerMap, "model2");
                    String serialNumber1 = getCellValueFromMapping(row, headerMap, "serial1");
                    String serialNumber2 = getCellValueFromMapping(row, headerMap, "serial2");
                    String locationName = getCellValueFromMapping(row, headerMap, "location");
                    
                    // GasGuard-specific fields
                    String systemName = getCellValueFromMapping(row, headerMap, "system");
                    String equipmentLocation = getCellValueFromMapping(row, headerMap, "equipmentLocation");
                    String configNumber = getCellValueFromMapping(row, headerMap, "config");
                    String equipmentSetStr = getCellValueFromMapping(row, headerMap, "equipmentSet");
                    
                    // System name inheritance: if current system is empty but previous row had a system, inherit it
                    if ((systemName == null || systemName.trim().isEmpty()) && lastSystemName != null) {
                        systemName = lastSystemName;
                        logger.debug("Row {}: Inherited system name '{}' from previous row", rowNum + 1, systemName);
                    } else if (systemName != null && !systemName.trim().isEmpty()) {
                        lastSystemName = systemName.trim(); // Update last system name for future inheritance
                    }
                    
                    // Auto-detect GasGuard tools (have Equipment Location, System is optional)
                    boolean isGasGuard = (equipmentLocation != null && !equipmentLocation.trim().isEmpty());
                    
                    // Skip rows where primary identifier is empty
                    String primaryName;
                    if (isGasGuard) {
                        // For GasGuard, use systemName if available, otherwise equipmentLocation
                        primaryName = (systemName != null && !systemName.trim().isEmpty()) ? 
                                     systemName : equipmentLocation;
                    } else {
                        primaryName = toolName;
                    }
                    
                    if (primaryName == null || primaryName.trim().isEmpty()) {
                        logger.debug("Skipping row {} - no primary identifier", rowNum + 1);
                        rowNum++;
                        continue;
                    }
                    
                    // Check if tool with this name already exists
                    if (toolRepository.findByName(primaryName.trim()).isPresent()) {
                        logger.warn("Tool '{}' already exists, skipping row {}", primaryName, rowNum + 1);
                        rowNum++;
                        continue;
                    }
                    
                    // Create new tool
                    Tool tool = new Tool();
                    
                    if (isGasGuard) {
                        // For GasGuard tools, handle cases where systemName might be empty
                        String gasGuardName;
                        if (systemName != null && !systemName.trim().isEmpty()) {
                            gasGuardName = systemName.trim();
                            tool.setSystemName(systemName.trim());
                        } else {
                            // Use equipment location as fallback name if system is empty
                            gasGuardName = equipmentLocation.trim();
                            tool.setSystemName(null); // Allow null system name
                        }
                        
                        tool.setName(gasGuardName);
                        tool.setToolType(Tool.ToolType.AMATGASGUARD);
                        tool.setEquipmentLocation(equipmentLocation.trim());
                        
                        // Set GasGuard-specific fields
                        if (configNumber != null && !configNumber.trim().isEmpty()) {
                            tool.setConfigNumber(configNumber.trim());
                        }
                        
                        if (equipmentSetStr != null && !equipmentSetStr.trim().isEmpty()) {
                            try {
                                // Parse equipment set as integer percentage - handle % symbol
                                String cleanStr = equipmentSetStr.trim().replaceAll("[^\\d.]", "");
                                if (!cleanStr.isEmpty()) {
                                    // Handle decimal percentages (e.g., "100.0" -> 100)
                                    double percentage = Double.parseDouble(cleanStr);
                                    tool.setEquipmentSet((int) Math.round(percentage));
                                }
                            } catch (NumberFormatException e) {
                                logger.warn("Invalid equipment set value '{}' for tool '{}' at row {}, defaulting to 100%", 
                                          equipmentSetStr, gasGuardName, rowNum + 1);
                                tool.setEquipmentSet(100); // Default to 100%
                            }
                        } else {
                            tool.setEquipmentSet(100); // Default to 100% if not specified
                        }
                        
                        logger.debug("Detected GasGuard tool '{}' with equipment location '{}' at row {}", 
                                   gasGuardName, equipmentLocation, rowNum + 1);
                    } else {
                        // For standard tools, use toolName as primary name
                        tool.setName(toolName.trim());
                    }
                    
                    if (toolName2 != null && !toolName2.trim().isEmpty()) {
                        tool.setSecondaryName(toolName2.trim());
                    }
                    
                    if (type != null && !type.trim().isEmpty()) {
                        Tool.ToolType normalizedType = normalizeToolType(type.trim());
                        if (normalizedType != null) {
                            tool.setToolType(normalizedType);
                            logger.debug("Normalized tool type '{}' to {} for tool '{}' at row {}", 
                                       type, normalizedType, toolName, rowNum + 1);
                        } else {
                            logger.warn("Invalid tool type '{}' for tool '{}' at row {}. Setting to null. " +
                                      "Supported values: CHEMBLEND (C, Chemrinse, Chem rinse, Chemblend, Chem Blend) " +
                                      "or SLURRY (Slurry, S, Feed, F)", 
                                      type, toolName, rowNum + 1);
                        }
                    }
                    
                    if (modelNumber1 != null && !modelNumber1.trim().isEmpty()) {
                        tool.setModel1(modelNumber1.trim());
                    }
                    
                    if (modelNumber2 != null && !modelNumber2.trim().isEmpty()) {
                        tool.setModel2(modelNumber2.trim());
                    }
                    
                    if (serialNumber1 != null && !serialNumber1.trim().isEmpty()) {
                        tool.setSerialNumber1(serialNumber1.trim());
                    }
                    
                    if (serialNumber2 != null && !serialNumber2.trim().isEmpty()) {
                        tool.setSerialNumber2(serialNumber2.trim());
                    }
                    
                    // Resolve location: Excel value if maps → user's active/default site → empty
                    String resolvedLocationName = null;
                    if (locationName != null && !locationName.trim().isEmpty()) {
                        Location matchedLocation = findLocationByName(locationName.trim());
                        if (matchedLocation != null) {
                            resolvedLocationName = matchedLocation.getDisplayName() != null ? matchedLocation.getDisplayName() : matchedLocation.getName();
                            logger.debug("Matched location '{}' to '{}' for tool '{}' at row {}", locationName, resolvedLocationName, toolName, rowNum + 1);
                        } else {
                            logger.warn("Could not match location '{}' for tool '{}' at row {}. Will try user active/default site.", locationName, toolName, rowNum + 1);
                        }
                    }
                    if (resolvedLocationName == null && defaultLocationOpt.isPresent()) {
                        Location userLoc = defaultLocationOpt.get();
                        resolvedLocationName = userLoc.getDisplayName() != null ? userLoc.getDisplayName() : userLoc.getName();
                        logger.debug("Using user's active/default location '{}' for tool '{}' at row {}", resolvedLocationName, toolName, rowNum + 1);
                    }
                    if (resolvedLocationName == null) {
                        resolvedLocationName = ""; // leave blank instead of hardcoding
                    }
                    tool.setLocationName(resolvedLocationName);
                    
                    // Set default status and dates
                    tool.setStatus(Tool.ToolStatus.NOT_STARTED);
                    tool.setSetDate(LocalDate.now());
                    
                    // Set upload date for GasGuard tools with incremental milliseconds
                    if (isGasGuard) {
                        // Calculate upload date: base time + (toolsCreated * 1 millisecond)
                        LocalDateTime uploadDateTime = baseUploadTime.plusNanos(toolsCreated * 1_000_000L); // 1 millisecond = 1,000,000 nanoseconds
                        tool.setUploadDate(uploadDateTime);
                        logger.debug("Set upload date for GasGuard tool '{}' to {} (offset: {} ms)", 
                                   primaryName, uploadDateTime, toolsCreated);
                    }
                    
                    // Save the tool
                    toolRepository.save(tool);
                    toolsCreated++;
                    
                    logger.debug("Created tool '{}' from row {}", primaryName, rowNum + 1);
                    
                } catch (Exception e) {
                    logger.error("Error processing row {}: {}", rowNum + 1, e.getMessage());
                    // Continue processing other rows
                }
                
                rowNum++;
            }
            
            logger.info("Excel tool creation completed. {} tools created", toolsCreated);
            return toolsCreated;
            
        } catch (IOException e) {
            logger.error("Error reading Excel file", e);
            throw new IllegalArgumentException("Error reading Excel file: " + e.getMessage());
        }
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // Handle numeric values that should be strings (like serial numbers)
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Convert numeric to string, removing decimal if it's a whole number
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
            default:
                return null;
        }
    }
    
    /**
     * Normalizes various tool type aliases to the correct ToolType enum values
     * CHEMBLEND aliases: C, Chemrinse, Chem rinse, Chemblend, Chem Blend
     * SLURRY aliases: Slurry, S, Feed, F
     */
    private Tool.ToolType normalizeToolType(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        
        // Normalize: trim, lowercase, remove extra spaces
        String normalized = input.trim().toLowerCase().replaceAll("\\s+", "");
        
        // CHEMBLEND aliases (case insensitive, space insensitive)
        switch (normalized) {
            case "c":
            case "chemrinse":
            case "chemblend":
            // Handle "chem rinse" -> "chemrinse" 
            case "chemrins":
            case "chemrin":
            // Handle "chem blend" -> "chemblend"
            case "chemblnd":
            case "chemblen":
            case "chemble":
                return Tool.ToolType.CHEMBLEND;
                
            // SLURRY aliases (case insensitive)    
            case "s":
            case "slurry":
            case "feed":
            case "f":
                return Tool.ToolType.SLURRY;

            // AMAT GasGuard aliases
            case "gasguard":
            case "amatgasguard":
            case "gasgd":
            case "gg":
                return Tool.ToolType.AMATGASGUARD;
                
            default:
                // Try exact enum match as fallback (for backward compatibility)
                try {
                    return Tool.ToolType.valueOf(input.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null; // Invalid type - will be logged as warning
                }
        }
    }

    /**
     * Enhanced duplicate detection logic
     * Checks multiple fields for potential duplicates:
     * 1. Exact tool name match (case insensitive)
     * 2. Serial number matches (either serial1 or serial2)
     * 3. Model number + partial name match
     */
    private Tool findPotentialDuplicate(Map<String, Object> rowData) {
        String toolName = (String) rowData.get("toolName");
        String serialNumber1 = (String) rowData.get("serialNumber1");
        String serialNumber2 = (String) rowData.get("serialNumber2");
        String modelNumber1 = (String) rowData.get("modelNumber1");
        String systemName = (String) rowData.get("systemName");
        String equipmentLocation = (String) rowData.get("equipmentLocation");
        Boolean isGasGuard = (Boolean) rowData.getOrDefault("isGasGuard", false);
        
        // For GasGuards, Equipment Location is the PRIMARY identifier - check this FIRST
        if (isGasGuard && equipmentLocation != null && !equipmentLocation.trim().isEmpty()) {
            // Normalize incoming location for robust comparison
            String incomingLocKey = normalizeLocationKey(equipmentLocation);

            // Ultra-robust first-pass: scan existing GasGuards and compare normalized keys
            // Catches formatting/spacing variants not handled by DB equality
            List<Tool> allGasGuards = toolRepository.findByToolType(Tool.ToolType.AMATGASGUARD);
            if (allGasGuards != null && !allGasGuards.isEmpty()) {
                for (Tool t : allGasGuards) {
                    String existingLocKey = normalizeLocationKey(t.getEquipmentLocation());
                    if (incomingLocKey.equals(existingLocKey)) {
                        logger.debug("Found GasGuard duplicate by Equipment Location: '{}' matches existing '{}'", equipmentLocation, t.getEquipmentLocation());
                        return t;
                    }
                }
            }
        }
        
        // Check 1: Exact tool name match (case insensitive) - only for non-GasGuards
        if (!isGasGuard && toolName != null && !toolName.isEmpty()) {
            Optional<Tool> byName = toolRepository.findByNameIgnoreCase(toolName);
            if (byName.isPresent()) {
                logger.debug("Found duplicate by name: {}", toolName);
                return byName.get();
            }
        }
        
        // Check 2: Serial number matches - only for non-GasGuards (GasGuards can share serials)
        if (!isGasGuard) {
            if (serialNumber1 != null && !serialNumber1.isEmpty()) {
                Optional<Tool> bySerial1 = toolRepository.findBySerialNumber1(serialNumber1);
                if (bySerial1.isPresent()) {
                    logger.debug("Found duplicate by serial number 1: {}", serialNumber1);
                    return bySerial1.get();
                }
                
                // Also check if the new serial1 matches any existing serial2
                Optional<Tool> bySerial2AsSerial1 = toolRepository.findBySerialNumber2(serialNumber1);
                if (bySerial2AsSerial1.isPresent()) {
                    logger.debug("Found duplicate by serial number 1 matching existing serial 2: {}", serialNumber1);
                    return bySerial2AsSerial1.get();
                }
            }
            
            if (serialNumber2 != null && !serialNumber2.isEmpty()) {
                Optional<Tool> bySerial2 = toolRepository.findBySerialNumber2(serialNumber2);
                if (bySerial2.isPresent()) {
                    logger.debug("Found duplicate by serial number 2: {}", serialNumber2);
                    return bySerial2.get();
                }
                
                // Also check if the new serial2 matches any existing serial1
                Optional<Tool> bySerial1AsSerial2 = toolRepository.findBySerialNumber1(serialNumber2);
                if (bySerial1AsSerial2.isPresent()) {
                    logger.debug("Found duplicate by serial number 2 matching existing serial 1: {}", serialNumber2);
                    return bySerial1AsSerial2.get();
                }
            }
        }

        // Check 3: Model number + partial name similarity (fuzzy matching)
        if (modelNumber1 != null && !modelNumber1.isEmpty() && toolName != null && toolName.length() > 3) {
            List<Tool> potentialMatches = toolRepository.findByModelAndNameSimilarity(modelNumber1, toolName);
            if (!potentialMatches.isEmpty()) {
                logger.debug("Found potential duplicate by model + name similarity: {} + {}", modelNumber1, toolName);
                return potentialMatches.get(0); // Return the first match
            }
        }
        
        return null;
    }

    /**
     * Tokenize serial numbers into a normalized set for comparison.
     * Splits on common delimiters (comma, ampersand, slash) and trims/uppercases.
     */
    private Set<String> tokenizeSerials(String... serials) {
        Set<String> tokens = new HashSet<>();
        if (serials == null) return tokens;
        for (String s : serials) {
            if (s == null) continue;
            String raw = s.trim();
            if (raw.isEmpty()) continue;
            String[] parts = raw.split("[,&/]");
            for (String p : parts) {
                String norm = p.trim();
                if (!norm.isEmpty()) {
                    tokens.add(norm.toUpperCase());
                }
            }
        }
        return tokens;
    }

    private String normalizeLocationKey(String location) {
        if (location == null) return "";
        return location
                .trim()
                .toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[\\-_/]", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private boolean disjoint(Set<String> a, Set<String> b) {
        if (a == null || b == null) return true;
        for (String x : a) {
            if (b.contains(x)) return false;
        }
        return true;
    }
    
    /**
     * Creates detailed comparison data for duplicate resolution UI
     */
    private Map<String, Object> createDuplicateComparisonData(Tool existingTool, Map<String, Object> newData) {
        Map<String, Object> comparison = new HashMap<>();
        
        // Basic info
        comparison.put("existingToolId", existingTool.getId());
        comparison.put("rowNumber", newData.get("rowNumber"));
        
        // Existing tool data (include GasGuard-specific fields when present)
        Map<String, Object> existing = new HashMap<>();
        existing.put("toolName", existingTool.getName());
        existing.put("toolName2", existingTool.getSecondaryName());
        existing.put("type", existingTool.getToolType() != null ? existingTool.getToolType().toString() : "");
        existing.put("modelNumber1", existingTool.getModel1());
        existing.put("modelNumber2", existingTool.getModel2());
        existing.put("serialNumber1", existingTool.getSerialNumber1());
        existing.put("serialNumber2", existingTool.getSerialNumber2());
        existing.put("location", existingTool.getLocationName() != null ? existingTool.getLocationName() : "");
        // GasGuard-specific
        existing.put("systemName", existingTool.getSystemName());
        existing.put("equipmentLocation", existingTool.getEquipmentLocation());
        existing.put("configNumber", existingTool.getConfigNumber());
        existing.put("equipmentSet", existingTool.getEquipmentSet());
        // Meta
        existing.put("status", existingTool.getStatus() != null ? existingTool.getStatus().toString() : "");
        existing.put("setDate", existingTool.getSetDate() != null ? existingTool.getSetDate().toString() : "");
        
        comparison.put("existing", existing);
        comparison.put("new", newData);
        
        // Highlight differences
        Map<String, Boolean> differences = new HashMap<>();
        differences.put("toolName", !Objects.equals(normalizeString(existingTool.getName()), normalizeString((String) newData.get("toolName"))));
        differences.put("toolName2", !Objects.equals(normalizeString(existingTool.getSecondaryName()), normalizeString((String) newData.get("toolName2"))));
        differences.put("type", !Objects.equals(
            existingTool.getToolType() != null ? existingTool.getToolType().toString() : "", 
            normalizeString((String) newData.get("type"))
        ));
        differences.put("modelNumber1", !Objects.equals(normalizeString(existingTool.getModel1()), normalizeString((String) newData.get("modelNumber1"))));
        differences.put("modelNumber2", !Objects.equals(normalizeString(existingTool.getModel2()), normalizeString((String) newData.get("modelNumber2"))));
        differences.put("serialNumber1", !Objects.equals(normalizeString(existingTool.getSerialNumber1()), normalizeString((String) newData.get("serialNumber1"))));
        differences.put("serialNumber2", !Objects.equals(normalizeString(existingTool.getSerialNumber2()), normalizeString((String) newData.get("serialNumber2"))));
        
        String existingLocation = existingTool.getLocationName() != null ? existingTool.getLocationName() : "";
        String newLocationName = (String) newData.get("locationName");
        Location newLocation = newLocationName != null && !newLocationName.isEmpty() ? findLocationByName(newLocationName) : null;
        String newLocationDisplay = newLocation != null ? newLocation.getDisplayName() : newLocationName;
        differences.put("location", !Objects.equals(normalizeString(existingLocation), normalizeString(newLocationDisplay)));
        
        comparison.put("differences", differences);
        
        // Determine duplicate reason
        List<String> reasons = new ArrayList<>();
        if (Objects.equals(normalizeString(existingTool.getName()), normalizeString((String) newData.get("toolName")))) {
            reasons.add("Exact name match");
        }
        if (Objects.equals(existingTool.getSerialNumber1(), newData.get("serialNumber1")) || 
            Objects.equals(existingTool.getSerialNumber2(), newData.get("serialNumber1")) ||
            Objects.equals(existingTool.getSerialNumber1(), newData.get("serialNumber2")) || 
            Objects.equals(existingTool.getSerialNumber2(), newData.get("serialNumber2"))) {
            reasons.add("Serial number match");
        }
        if (Objects.equals(existingTool.getModel1(), newData.get("modelNumber1"))) {
            reasons.add("Model number match");
        }
        
        comparison.put("duplicateReasons", reasons);
        comparison.put("action", "overwrite"); // Default action
        
        return comparison;
    }
    
    /**
     * Normalize strings for comparison (null-safe, trimmed, lowercase)
     */
    private String normalizeString(String str) {
        return str == null ? "" : str.trim().toLowerCase();
    }

    /**
     * Finds a location by name using flexible matching for common variations
     * Supports variations like:
     * - "Arizona Fab 52", "AZ F52", "AZ52", "AZF52"
     * - "New Mexico Fab 11", "NM F11", "NM11", "NMF11"
     * - "Ireland Fab 24", "IE F24", "IE24", "IEF24"
     */
    private Location findLocationByName(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return null;
        }
        
        // Get all locations to match against
        List<Location> allLocations = locationRepository.findAll();
        
        // Normalize the input for matching
        String normalized = locationName.trim().toLowerCase()
            .replaceAll("\\s+", "") // Remove all spaces
            .replaceAll("fab", "f") // Convert "fab" to "f"  
            .replaceAll("factory", "f") // Convert "factory" to "f"
            .replaceAll("[^a-z0-9]", ""); // Remove special characters
        
        logger.debug("Normalized location input '{}' to '{}'", locationName, normalized);
        
        // Try to match against each location
        for (Location location : allLocations) {
            if (matchesLocation(location, normalized)) {
                return location;
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a normalized location name matches a specific location
     */
    private boolean matchesLocation(Location location, String normalizedInput) {
        String state = location.getState();
        String fab = location.getFab();
        
        if (state == null || fab == null) {
            return false;
        }
        
        // Generate various patterns for this location
        String stateAbbr = getStateAbbreviation(state);
        String stateLower = state.toLowerCase();
        String fabNumber = fab.toLowerCase();
        
        // Patterns to match (all normalized, no spaces, lowercase)
        String[] patterns = {
            // Full state name patterns
            stateLower + "fab" + fabNumber,           // "arizonafab52"
            stateLower + "f" + fabNumber,             // "arizonaf52"
            stateLower + fabNumber,                   // "arizona52"
            
            // State abbreviation patterns  
            stateAbbr.toLowerCase() + "fab" + fabNumber,  // "azfab52"
            stateAbbr.toLowerCase() + "f" + fabNumber,    // "azf52"
            stateAbbr.toLowerCase() + fabNumber,          // "az52"
            
            // Display name pattern (what's shown in UI)
            stateAbbr.toLowerCase() + "f" + fabNumber     // "azf52" (same as above but explicit)
        };
        
        // Check if normalized input matches any pattern
        for (String pattern : patterns) {
            if (pattern.equals(normalizedInput)) {
                logger.debug("Matched location pattern '{}' for {}", pattern, location.getDisplayName());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Gets the abbreviation for a state name
     */
    private String getStateAbbreviation(String stateName) {
        if (stateName == null) return "";
        
        switch (stateName.toLowerCase()) {
            case "arizona":
                return "AZ";
            case "new mexico":
                return "NM";
            case "ireland":
                return "IE";
            default:
                // Fallback to first 2 characters
                return stateName.length() >= 2 ? stateName.substring(0, 2).toUpperCase() : stateName.toUpperCase();
        }
    }

    /**
     * Process Excel upload with duplicate resolutions
     * Creates new tools and updates existing ones based on user choices
     */
    @Transactional
    @CacheEvict(value = {"tools-list", "dashboard-data"}, allEntries = true)
    public Map<String, Object> processExcelWithDuplicateResolutions(
            List<Map<String, Object>> validRows, 
            List<Map<String, Object>> duplicateResolutions,
            String currentUserName) throws Exception {
        
        logger.info("Processing Excel upload with {} valid rows and {} duplicate resolutions", 
                   validRows.size(), duplicateResolutions.size());
        
        Map<String, Object> result = new HashMap<>();
        int toolsCreated = 0;
        int toolsUpdated = 0;
        int toolsSkipped = 0;
        List<String> auditMessages = new ArrayList<>();
        
        // Find default location for new tools
        Optional<Location> defaultLocationOpt = locationRepository.findByDefaultLocationIsTrue();
        // Base time for deterministic millisecond offsets on created tools
        LocalDateTime baseUploadTime = LocalDateTime.now();
        
        try {
            // Process valid new tools first
            for (Map<String, Object> rowData : validRows) {
                try {
                    Tool newTool = createToolFromRowData(rowData, defaultLocationOpt);
                    // If we are creating a GasGuard tool, set uploadDate with millisecond offsets
                    if (newTool.getToolType() == Tool.ToolType.AMATGASGUARD && newTool.getUploadDate() == null) {
                        LocalDateTime uploadDateTime = baseUploadTime.plusNanos((long) toolsCreated * 1_000_000L);
                        newTool.setUploadDate(uploadDateTime);
                    }
                    toolRepository.save(newTool);
                    toolsCreated++;
                    logger.debug("Created new tool: {}", newTool.getName());
                } catch (Exception e) {
                    logger.error("Error creating tool from row {}: {}", rowData.get("rowNumber"), e.getMessage());
                }
            }
            
            // Process duplicate resolutions
            for (Map<String, Object> resolution : duplicateResolutions) {
                String action = (String) resolution.get("action");
                Long existingToolId = ((Number) resolution.get("existingToolId")).longValue();
                Map<String, Object> newData = (Map<String, Object>) resolution.get("new");
                
                if ("skip".equals(action)) {
                    toolsSkipped++;
                    logger.debug("Skipped duplicate for existing tool ID: {}", existingToolId);
                    continue;
                }
                
                if ("overwrite".equals(action)) {
                    try {
                        String auditMessage = updateExistingTool(existingToolId, newData, currentUserName);
                        auditMessages.add(auditMessage);
                        toolsUpdated++;
                        logger.debug("Updated existing tool ID: {}", existingToolId);
                    } catch (Exception e) {
                        logger.error("Error updating tool ID {}: {}", existingToolId, e.getMessage());
                        toolsSkipped++;
                    }
                }
            }
            
            result.put("success", true);
            result.put("toolsCreated", toolsCreated);
            result.put("toolsUpdated", toolsUpdated);
            result.put("toolsSkipped", toolsSkipped);
            result.put("auditMessages", auditMessages);
            result.put("totalProcessed", toolsCreated + toolsUpdated + toolsSkipped);
            
            logger.info("Excel processing completed. Created: {}, Updated: {}, Skipped: {}", 
                       toolsCreated, toolsUpdated, toolsSkipped);
            
        } catch (Exception e) {
            logger.error("Error processing Excel upload: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Error processing upload: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Creates a new Tool entity from row data
     */
    private Tool createToolFromRowData(Map<String, Object> rowData, Optional<Location> defaultLocationOpt) {
        Tool tool = new Tool();
        
        // Check if this is a GasGuard tool
        boolean isGasGuard = (Boolean) rowData.getOrDefault("isGasGuard", false);
        String primaryName = (String) rowData.get("primaryName");
        
        if (isGasGuard) {
            // Handle GasGuard tools
            String systemName = (String) rowData.get("systemName");
            String equipmentLocation = (String) rowData.get("equipmentLocation");
            
            // Use primaryName which was calculated during analysis
            tool.setName(primaryName != null ? primaryName.trim() : "");
            tool.setToolType(Tool.ToolType.AMATGASGUARD);
            
            // Set GasGuard-specific fields
            if (systemName != null && !systemName.trim().isEmpty()) {
                tool.setSystemName(systemName.trim());
            }
            if (equipmentLocation != null && !equipmentLocation.trim().isEmpty()) {
                tool.setEquipmentLocation(equipmentLocation.trim());
            }
            
            String configNumber = (String) rowData.get("configNumber");
            if (configNumber != null && !configNumber.trim().isEmpty()) {
                tool.setConfigNumber(configNumber.trim());
            }
            
            String equipmentSetStr = (String) rowData.get("equipmentSet");
            if (equipmentSetStr != null && !equipmentSetStr.trim().isEmpty()) {
                try {
                    // Parse equipment set as integer percentage - handle % symbol
                    String cleanStr = equipmentSetStr.trim().replaceAll("[^\\d.]", "");
                    if (!cleanStr.isEmpty()) {
                        // Handle decimal percentages (e.g., "100.0" -> 100)
                        double percentage = Double.parseDouble(cleanStr);
                        tool.setEquipmentSet((int) Math.round(percentage));
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid equipment set value '{}' for tool '{}', defaulting to 100%", 
                              equipmentSetStr, primaryName);
                    tool.setEquipmentSet(100); // Default to 100%
                }
            } else {
                tool.setEquipmentSet(100); // Default to 100% if not specified
            }
        } else {
            // Handle standard tools
            String toolName = (String) rowData.get("toolName");
            tool.setName(toolName != null ? toolName.trim() : "");
            
            String type = (String) rowData.get("type");
            if (type != null && !type.trim().isEmpty()) {
                Tool.ToolType normalizedType = normalizeToolType(type.trim());
                if (normalizedType != null) {
                    tool.setToolType(normalizedType);
                }
            }
        }
        
        // Common properties for both tool types
        String toolName2 = (String) rowData.get("toolName2");
        if (toolName2 != null && !toolName2.trim().isEmpty()) {
            tool.setSecondaryName(toolName2.trim());
        }
        
        String modelNumber1 = (String) rowData.get("modelNumber1");
        if (modelNumber1 != null && !modelNumber1.trim().isEmpty()) {
            tool.setModel1(modelNumber1.trim());
        }
        
        String modelNumber2 = (String) rowData.get("modelNumber2");
        if (modelNumber2 != null && !modelNumber2.trim().isEmpty()) {
            tool.setModel2(modelNumber2.trim());
        }
        
        String serialNumber1 = (String) rowData.get("serialNumber1");
        if (serialNumber1 != null && !serialNumber1.trim().isEmpty()) {
            tool.setSerialNumber1(serialNumber1.trim());
        }
        
        String serialNumber2 = (String) rowData.get("serialNumber2");
        if (serialNumber2 != null && !serialNumber2.trim().isEmpty()) {
            tool.setSerialNumber2(serialNumber2.trim());
        }
        
        // Handle location - prefer Excel value mapped; else current user's active/default site; else blank
        String locationName = (String) rowData.get("locationName");
        String resolvedLocationName = null;
        if (locationName != null && !locationName.trim().isEmpty()) {
            Location matchedLocation = findLocationByName(locationName.trim());
            if (matchedLocation != null) {
                resolvedLocationName = matchedLocation.getDisplayName() != null ? matchedLocation.getDisplayName() : matchedLocation.getName();
            }
        }
        if (resolvedLocationName == null) {
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getName() != null) {
                    Optional<User> userOpt = userRepository.findByEmailIgnoreCase(auth.getName());
                    if (userOpt.isPresent()) {
                        User current = userOpt.get();
                        Location userLoc = current.getActiveSite() != null ? current.getActiveSite() : current.getDefaultLocation();
                        if (userLoc != null) {
                            resolvedLocationName = userLoc.getDisplayName() != null ? userLoc.getDisplayName() : userLoc.getName();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        tool.setLocationName(resolvedLocationName != null ? resolvedLocationName : "");
        
        // Set default status and dates
        tool.setStatus(Tool.ToolStatus.NOT_STARTED);
        tool.setSetDate(LocalDate.now());
        
        return tool;
    }
    
    /**
     * Updates an existing tool with new data and creates audit trail
     */
    private String updateExistingTool(Long toolId, Map<String, Object> newData, String currentUserName) {
        Optional<Tool> toolOpt = toolRepository.findById(toolId);
        if (!toolOpt.isPresent()) {
            throw new RuntimeException("Tool not found with ID: " + toolId);
        }
        
        Tool tool = toolOpt.get();
        List<String> changes = new ArrayList<>();
        
        // Track and update each field
        String newToolName = (String) newData.get("toolName");
        // Do not update name for GasGuard tools via Excel duplicate overwrite
        if (tool.getToolType() != Tool.ToolType.AMATGASGUARD) {
            if (newToolName != null && !newToolName.trim().isEmpty() &&
                !Objects.equals(tool.getName(), newToolName.trim())) {
                changes.add(String.format("Name: '%s' → '%s'", tool.getName(), newToolName.trim()));
                tool.setName(newToolName.trim());
            }
        }
        
        String newToolName2 = (String) newData.get("toolName2");
        // Do not update secondary name for GasGuard tools via Excel duplicate overwrite
        if (tool.getToolType() != Tool.ToolType.AMATGASGUARD) {
            if (!Objects.equals(tool.getSecondaryName(), newToolName2)) {
                String oldValue = tool.getSecondaryName() != null ? tool.getSecondaryName() : "(empty)";
                String newValue = newToolName2 != null && !newToolName2.trim().isEmpty() ? newToolName2.trim() : "(empty)";
                changes.add(String.format("Secondary Name: '%s' → '%s'", oldValue, newValue));
                tool.setSecondaryName(newToolName2 != null && !newToolName2.trim().isEmpty() ? newToolName2.trim() : null);
            }
        }
        
        String newType = (String) newData.get("type");
        Tool.ToolType currentType = tool.getToolType();
        Tool.ToolType newToolType = newType != null && !newType.trim().isEmpty() ? normalizeToolType(newType.trim()) : null;
        // Preserve GasGuard type on overwrite; never downgrade or null it out
        if (currentType == Tool.ToolType.AMATGASGUARD) {
            // No change to type
        } else {
            if (newToolType != null && !Objects.equals(currentType, newToolType)) {
                String oldValue = currentType != null ? currentType.toString() : "(empty)";
                String newValue = newToolType.toString();
                changes.add(String.format("Type: '%s' → '%s'", oldValue, newValue));
                tool.setToolType(newToolType);
            }
        }
        
        String newModel1 = (String) newData.get("modelNumber1");
        if (!Objects.equals(tool.getModel1(), newModel1)) {
            String oldValue = tool.getModel1() != null ? tool.getModel1() : "(empty)";
            String newValue = newModel1 != null && !newModel1.trim().isEmpty() ? newModel1.trim() : "(empty)";
            changes.add(String.format("Model 1: '%s' → '%s'", oldValue, newValue));
            tool.setModel1(newModel1 != null && !newModel1.trim().isEmpty() ? newModel1.trim() : null);
        }
        
        String newModel2 = (String) newData.get("modelNumber2");
        if (!Objects.equals(tool.getModel2(), newModel2)) {
            String oldValue = tool.getModel2() != null ? tool.getModel2() : "(empty)";
            String newValue = newModel2 != null && !newModel2.trim().isEmpty() ? newModel2.trim() : "(empty)";
            changes.add(String.format("Model 2: '%s' → '%s'", oldValue, newValue));
            tool.setModel2(newModel2 != null && !newModel2.trim().isEmpty() ? newModel2.trim() : null);
        }
        
        String newSerial1 = (String) newData.get("serialNumber1");
        if (!Objects.equals(tool.getSerialNumber1(), newSerial1)) {
            String oldValue = tool.getSerialNumber1() != null ? tool.getSerialNumber1() : "(empty)";
            String newValue = newSerial1 != null && !newSerial1.trim().isEmpty() ? newSerial1.trim() : "(empty)";
            changes.add(String.format("Serial 1: '%s' → '%s'", oldValue, newValue));
            tool.setSerialNumber1(newSerial1 != null && !newSerial1.trim().isEmpty() ? newSerial1.trim() : null);
        }
        
        String newSerial2 = (String) newData.get("serialNumber2");
        if (!Objects.equals(tool.getSerialNumber2(), newSerial2)) {
            String oldValue = tool.getSerialNumber2() != null ? tool.getSerialNumber2() : "(empty)";
            String newValue = newSerial2 != null && !newSerial2.trim().isEmpty() ? newSerial2.trim() : "(empty)";
            changes.add(String.format("Serial 2: '%s' → '%s'", oldValue, newValue));
            tool.setSerialNumber2(newSerial2 != null && !newSerial2.trim().isEmpty() ? newSerial2.trim() : null);
        }
        
        // Handle location update
        String newLocationName = (String) newData.get("locationName");
        String finalLocationName = tool.getLocationName();
        if (newLocationName != null && !newLocationName.trim().isEmpty()) {
            Location matchedLocation = findLocationByName(newLocationName.trim());
            if (matchedLocation != null) {
                finalLocationName = matchedLocation.getDisplayName() != null ? matchedLocation.getDisplayName() : matchedLocation.getName();
            }
        } else {
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getName() != null) {
                    Optional<User> userOpt = userRepository.findByEmailIgnoreCase(auth.getName());
                    if (userOpt.isPresent()) {
                        User current = userOpt.get();
                        Location userLoc = current.getActiveSite() != null ? current.getActiveSite() : current.getDefaultLocation();
                        if (userLoc != null) {
                            finalLocationName = userLoc.getDisplayName() != null ? userLoc.getDisplayName() : userLoc.getName();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        
        if (!Objects.equals(tool.getLocationName(), finalLocationName)) {
            String oldValue = tool.getLocationName() != null ? tool.getLocationName() : "(empty)";
            changes.add(String.format("Location: '%s' → '%s'", oldValue, finalLocationName));
            tool.setLocationName(finalLocationName);
        }
        
        // Save the updated tool
        toolRepository.save(tool);
        
        // Create audit comment if there were changes
        if (!changes.isEmpty()) {
            String changesSummary = String.join(", ", changes);
            String commentText = String.format("Tool updated via Excel upload by %s. Changes: %s", 
                                             currentUserName != null ? currentUserName : "System", 
                                             changesSummary);
            
            // Create and save the comment
            ToolComment comment = new ToolComment();
            comment.setTool(tool);
            comment.setContent(commentText);
            comment.setCreatedDate(LocalDateTime.now());
            comment.setSystemGenerated(true);
            
            // Try to set the user if available
            if (currentUserName != null) {
                Optional<User> userOpt = userRepository.findByEmailIgnoreCase(currentUserName);
                userOpt.ifPresent(comment::setUser);
            }
            
            toolCommentRepository.save(comment);
            
            return String.format("Updated tool '%s' (ID: %d): %s", tool.getName(), tool.getId(), changesSummary);
        }
        
        return String.format("No changes needed for tool '%s' (ID: %d)", tool.getName(), tool.getId());
    }
    
    /**
     * Flexible pattern matching for header names
     */
    private boolean matchesPattern(String headerValue, String... patterns) {
        String normalized = headerValue.toLowerCase().replaceAll("[\\s#_-]", "");
        
        for (String pattern : patterns) {
            String normalizedPattern = pattern.toLowerCase().replaceAll("[\\s#_-]", "");
            if (normalized.contains(normalizedPattern) || normalizedPattern.contains(normalized)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get cell value from flexible header mapping
     */
    private String getCellValueFromMapping(Row row, Map<String, Integer> headerMap, String fieldName) {
        Integer columnIndex = headerMap.get(fieldName);
        if (columnIndex == null) {
            return null;
        }
        return getCellValueAsString(row.getCell(columnIndex));
    }
}