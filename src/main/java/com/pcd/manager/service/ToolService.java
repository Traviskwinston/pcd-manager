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
import java.io.InputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.web.multipart.MultipartFile;

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

    @CacheEvict(value = {"tools-list", "dropdown-data", "tool-details"}, allEntries = true)
    public void deleteTool(Long id) {
        logger.info("Deleting tool {} and evicting caches", id);
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
    @CacheEvict(value = {"tool-list", "dashboard-data"}, allEntries = true)  
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
            
            // Expected headers in order
            String[] expectedHeaders = {
                "Tool Name", "Tool Name 2", "Type", "Model Number 1", 
                "Model Number 2", "Serial Number 1", "Serial Number 2"
            };
            
            // Validate header format
            for (int i = 0; i < expectedHeaders.length; i++) {
                Cell cell = headerRow.getCell(i);
                String cellValue = cell != null ? cell.getStringCellValue().trim() : "";
                
                if (!expectedHeaders[i].equals(cellValue)) {
                    throw new IllegalArgumentException(
                        String.format("Invalid header at column %c. Expected '%s' but found '%s'. " +
                                    "Please ensure headers match exactly: %s", 
                                    (char)('A' + i), expectedHeaders[i], cellValue, 
                                    String.join(", ", expectedHeaders))
                    );
                }
            }
            
            logger.info("Excel headers validated successfully");
            
            // Find default location for new tools
            Optional<Location> defaultLocationOpt = locationRepository.findByDefaultLocationIsTrue();
            if (!defaultLocationOpt.isPresent()) {
                logger.warn("No default location found. Tools will be created without location assignment.");
            }
            
            int toolsCreated = 0;
            int rowNum = 1; // Start after header row
            
            while (rowNum <= sheet.getLastRowNum()) {
                Row row = sheet.getRow(rowNum);
                
                if (row == null) {
                    rowNum++;
                    continue;
                }
                
                try {
                    // Extract data from row
                    String toolName = getCellValueAsString(row.getCell(0));
                    String toolName2 = getCellValueAsString(row.getCell(1));
                    String type = getCellValueAsString(row.getCell(2));
                    String modelNumber1 = getCellValueAsString(row.getCell(3));
                    String modelNumber2 = getCellValueAsString(row.getCell(4));
                    String serialNumber1 = getCellValueAsString(row.getCell(5));
                    String serialNumber2 = getCellValueAsString(row.getCell(6));
                    
                    // Skip rows where tool name is empty
                    if (toolName == null || toolName.trim().isEmpty()) {
                        logger.debug("Skipping row {} - no tool name", rowNum + 1);
                        rowNum++;
                        continue;
                    }
                    
                    // Check if tool with this name already exists
                    if (toolRepository.findByName(toolName.trim()).isPresent()) {
                        logger.warn("Tool '{}' already exists, skipping row {}", toolName, rowNum + 1);
                        rowNum++;
                        continue;
                    }
                    
                    // Create new tool
                    Tool tool = new Tool();
                    tool.setName(toolName.trim());
                    
                    if (toolName2 != null && !toolName2.trim().isEmpty()) {
                        tool.setSecondaryName(toolName2.trim());
                    }
                    
                    if (type != null && !type.trim().isEmpty()) {
                        try {
                            tool.setToolType(Tool.ToolType.valueOf(type.trim().toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            logger.warn("Invalid tool type '{}' for tool '{}' at row {}. Setting to null.", 
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
                    
                    // Assign default location if available
                    defaultLocationOpt.ifPresent(tool::setLocation);
                    
                    // Set default status and dates
                    tool.setStatus(Tool.ToolStatus.NOT_STARTED);
                    tool.setSetDate(LocalDate.now());
                    
                    // Save the tool
                    toolRepository.save(tool);
                    toolsCreated++;
                    
                    logger.debug("Created tool '{}' from row {}", toolName, rowNum + 1);
                    
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
} 