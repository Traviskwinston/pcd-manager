package com.pcd.manager.service;

import com.pcd.manager.model.Tool;
import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaDocument;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.MovingPart;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.repository.RmaDocumentRepository;
import com.pcd.manager.repository.RmaPictureRepository;
import com.pcd.manager.repository.MovingPartRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ToolService {

    private static final Logger logger = LoggerFactory.getLogger(ToolService.class);

    private final ToolRepository toolRepository;
    private final RmaRepository rmaRepository;
    private final RmaDocumentRepository documentRepository;
    private final RmaPictureRepository pictureRepository;
    private final MovingPartRepository movingPartRepository;
    private PassdownService passdownService; // Not final anymore, will be set by setter

    @Autowired
    public ToolService(ToolRepository toolRepository, 
                      RmaRepository rmaRepository,
                      RmaDocumentRepository documentRepository,
                      RmaPictureRepository pictureRepository,
                      MovingPartRepository movingPartRepository) {
        this.toolRepository = toolRepository;
        this.rmaRepository = rmaRepository;
        this.documentRepository = documentRepository;
        this.pictureRepository = pictureRepository;
        this.movingPartRepository = movingPartRepository;
        // PassdownService will be injected via setter
    }
    
    // Setter method for PassdownService
    @Autowired
    public void setPassdownService(PassdownService passdownService) {
        this.passdownService = passdownService;
    }

    public List<Tool> getAllTools() {
        return toolRepository.findAll();
    }

    /**
     * Get all tools with technicians eagerly loaded
     */
    public List<Tool> getAllToolsWithTechnicians() {
        return toolRepository.findAllWithTechnicians();
    }

    public Optional<Tool> getToolById(Long id) {
        return toolRepository.findById(id);
    }

    public Tool saveTool(Tool tool) {
        return toolRepository.save(tool);
    }

    public void deleteTool(Long id) {
        toolRepository.deleteById(id);
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
} 