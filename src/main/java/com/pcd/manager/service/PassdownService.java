package com.pcd.manager.service;

import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.repository.PassdownRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.repository.RmaPictureRepository;
import com.pcd.manager.util.UploadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;

@Service
public class PassdownService {

    private static final Logger logger = LoggerFactory.getLogger(PassdownService.class);
    private final PassdownRepository passdownRepository;
    private ToolService toolService; // Not final anymore, will be set by setter
    private final UploadUtils uploadUtils;
    private final ToolRepository toolRepository;
    private final RmaRepository rmaRepository;
    private final RmaPictureRepository pictureRepository;

    @Autowired
    public PassdownService(PassdownRepository passdownRepository, UploadUtils uploadUtils,
                          ToolRepository toolRepository, RmaRepository rmaRepository, RmaPictureRepository pictureRepository) {
        this.passdownRepository = passdownRepository;
        this.uploadUtils = uploadUtils;
        this.toolRepository = toolRepository;
        this.rmaRepository = rmaRepository;
        this.pictureRepository = pictureRepository;
        // ToolService will be injected via setter
    }
    
    // Setter method for ToolService
    @Autowired
    public void setToolService(ToolService toolService) {
        this.toolService = toolService;
    }

    public List<Passdown> getAllPassdowns() {
        return passdownRepository.findAllByOrderByDateDesc();
    }

    public Optional<Passdown> getPassdownById(Long id) {
        return passdownRepository.findById(id);
    }

    /**
     * Retrieves a passdown by ID with all collections and associations fully loaded.
     * This ensures proper handling of collections during updates.
     * 
     * @param id The ID of the passdown to retrieve
     * @return Optional containing the passdown with all details loaded
     */
    @Transactional(readOnly = true)
    public Optional<Passdown> getPassdownByIdWithDetails(Long id) {
        logger.debug("Loading passdown with details for ID: {}", id);
        Optional<Passdown> passdownOpt = passdownRepository.findById(id);
        
        if (passdownOpt.isPresent()) {
            Passdown passdown = passdownOpt.get();
            
            // Force initialization of collections
            if (passdown.getPicturePaths() != null) {
                passdown.getPicturePaths().size();
                logger.debug("Loaded {} picture paths", passdown.getPicturePaths().size());
            }
            
            if (passdown.getPictureNames() != null) {
                passdown.getPictureNames().size();
                logger.debug("Loaded {} picture names", passdown.getPictureNames().size());
            }
            
            return Optional.of(passdown);
        }
        
        return passdownOpt;
    }

    public List<Passdown> getPassdownsByDate(LocalDate date) {
        logger.info("Fetching passdowns for date: {}", date);
        return passdownRepository.findByDateOrderByDateDesc(date);
    }

    public List<Passdown> getPassdownsByDateRange(LocalDate startDate, LocalDate endDate) {
        logger.debug("Getting passdowns between {} and {}", startDate, endDate);
        return passdownRepository.findByDateBetweenOrderByDateDesc(startDate, endDate);
    }

    /**
     * Gets the most recent passdowns limited by count
     * @param count Number of passdowns to return
     * @return List of the most recent passdowns
     */
    public List<Passdown> getRecentPassdowns(int count) {
        logger.debug("Getting {} most recent passdowns", count);
        return passdownRepository.findAll(PageRequest.of(0, count, Sort.by(Sort.Direction.DESC, "date")))
                .getContent();
    }

    @Transactional
    public Passdown savePassdown(Passdown passdownData, User currentUser, String newPicturePath, String originalFilename) {
        Passdown passdownToSave;
        Tool selectedTool = null;

        if (passdownData.getTool() != null && passdownData.getTool().getId() != null) {
            Long toolId = passdownData.getTool().getId();
            selectedTool = toolService.getToolById(toolId)
                .orElseThrow(() -> new RuntimeException("Selected Tool not found: " + toolId));
             logger.debug("Resolved selected tool: ID {}", toolId);
        } else {
             logger.debug("No tool selected or tool ID was null.");
        }

        if (passdownData.getId() != null) {
            logger.info("Updating existing passdown ID: {}", passdownData.getId());
            // Fetch the existing entity with collections fully loaded
            passdownToSave = getPassdownByIdWithDetails(passdownData.getId())
                 .orElseThrow(() -> new RuntimeException("Passdown not found for update: " + passdownData.getId()));
            
            // Update basic fields
            passdownToSave.setComment(passdownData.getComment());
            passdownToSave.setDate(passdownData.getDate());
            
            // Only update the tool if a valid tool was actually selected in the submitted data
            if (selectedTool != null) {
                logger.debug("Updating tool association for passdown ID: {} to Tool ID: {}", passdownToSave.getId(), selectedTool.getId());
                passdownToSave.setTool(selectedTool);
            } else if (passdownData.getTool() == null || passdownData.getTool().getId() == null) {
                // If no tool info was submitted (e.g., during picture delete), explicitly set tool to null ONLY IF it was intended
                // Check if the intention was to clear the tool
                boolean clearToolIntended = (passdownData.getTool() != null && passdownData.getTool().getId() == null); 
                if (clearToolIntended) {
                    logger.debug("Clearing tool association for passdown ID: {}", passdownToSave.getId());
                    passdownToSave.setTool(null);
                } else {
                    logger.debug("No tool information submitted, preserving existing tool for passdown ID: {}", passdownToSave.getId());
                }
            } else {
                 logger.debug("No valid tool selected in submitted data, preserving existing tool for passdown ID: {}", passdownToSave.getId());
            }
            
            // Make sure the collections are initialized
            if (passdownToSave.getPicturePaths() == null) {
                passdownToSave.setPicturePaths(new HashSet<>());
            }
            if (passdownToSave.getPictureNames() == null) {
                passdownToSave.setPictureNames(new HashMap<>());
            }
        } else {
             logger.info("Creating new passdown");
            passdownToSave = passdownData;
            passdownToSave.setTool(selectedTool);
            // Initialize collections for new entities
            passdownToSave.setPicturePaths(new HashSet<>());
            passdownToSave.setPictureNames(new HashMap<>());
        }

        passdownToSave.setUser(currentUser);

        // Add new picture if provided
        if (newPicturePath != null && originalFilename != null) {
             logger.debug("Adding picture {} ({}) to passdown ID: {}", newPicturePath, originalFilename, passdownToSave.getId());
             passdownToSave.getPicturePaths().add(newPicturePath);
             passdownToSave.getPictureNames().put(newPicturePath, originalFilename);
             
             // If a tool is selected, also add the picture to the tool
             if (selectedTool != null) {
                 logger.debug("Also adding picture to associated tool ID: {}", selectedTool.getId());
                 // Initialize collections if needed
                 if (selectedTool.getPicturePaths() == null) {
                     selectedTool.setPicturePaths(new HashSet<>());
                 }
                 if (selectedTool.getPictureNames() == null) {
                     selectedTool.setPictureNames(new HashMap<>());
                 }
                 
                 // Add the picture to the tool with a prefix to indicate it's from a passdown
                 selectedTool.getPicturePaths().add(newPicturePath);
                 
                 // Format the passdown info to include user and date
                 String userInfo = currentUser != null ? (currentUser.getName() != null ? currentUser.getName() : currentUser.getEmail()) : "Unknown User";
                 String dateInfo = passdownToSave.getDate() != null ? 
                     passdownToSave.getDate().format(java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy")) : 
                     java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                 
                 selectedTool.getPictureNames().put(newPicturePath, "Passdown: " + userInfo + " " + dateInfo);
                 
                 // Save the tool
                 toolService.saveTool(selectedTool);
                 logger.debug("Successfully added passdown picture to tool ID: {}", selectedTool.getId());
             }
        }

        logger.debug("Calling repository.save for passdown ID: {}, has {} pictures", 
            passdownToSave.getId(), passdownToSave.getPicturePaths().size());
        try {
            Passdown saved = passdownRepository.save(passdownToSave);
            logger.info("Successfully saved passdown ID: {}", saved.getId());
            return saved;
        } catch (Exception e) {
            logger.error("Error during repository.save for passdown ID {}: {}", passdownToSave.getId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Save a passdown entity after updating its document or picture links
     * Used for linking between RMA and Passdown
     * 
     * @param passdown The passdown to save
     * @return The saved passdown
     */
    @Transactional
    public Passdown savePassdown(Passdown passdown) {
        logger.debug("Saving passdown ID: {} after updating links", passdown.getId());
        return passdownRepository.save(passdown);
    }
    
    /**
     * Update passdown when deleting a picture
     */
    @Transactional
    public Passdown savePassdownForDelete(Passdown passdown) {
        logger.debug("Saving passdown ID: {} after deleting pictures", passdown.getId());
        
        // Make sure the collections are initialized
        if (passdown.getPicturePaths() == null) {
            passdown.setPicturePaths(new HashSet<>());
        }
        if (passdown.getPictureNames() == null) {
            passdown.setPictureNames(new HashMap<>());
        }
        if (passdown.getDocumentPaths() == null) {
            passdown.setDocumentPaths(new HashSet<>());
        }
        if (passdown.getDocumentNames() == null) {
            passdown.setDocumentNames(new HashMap<>());
        }
        
        return passdownRepository.save(passdown);
    }

    @Transactional
    public void deletePassdown(Long id) {
        logger.info("Deleting passdown with ID: {}", id);
        
        // First, get the passdown with all details to handle associated pictures
        Optional<Passdown> passdownOpt = getPassdownByIdWithDetails(id);
        
        if (passdownOpt.isPresent()) {
            Passdown passdown = passdownOpt.get();
            Tool associatedTool = passdown.getTool();
            
            // Delete associated pictures from the file system
            if (passdown.getPicturePaths() != null && !passdown.getPicturePaths().isEmpty()) {
                logger.info("Checking {} picture(s) for passdown ID: {}", passdown.getPicturePaths().size(), id);
                
                for (String picturePath : passdown.getPicturePaths()) {
                    try {
                        boolean shouldDeletePhysicalFile = true;
                        
                        // Check if picture is associated with a tool
                        if (associatedTool != null) {
                            // Get fresh tool data to ensure collections are loaded
                            Optional<Tool> toolOpt = toolService.getToolById(associatedTool.getId());
                            if (toolOpt.isPresent()) {
                                Tool tool = toolOpt.get();
                                if (tool.getPicturePaths() != null && tool.getPicturePaths().contains(picturePath)) {
                                    logger.info("Picture {} is still in use by tool ID: {}, skipping physical deletion", 
                                        picturePath, tool.getId());
                                    shouldDeletePhysicalFile = false;
                                }
                            }
                        }
                        
                        if (shouldDeletePhysicalFile) {
                            boolean deleted = uploadUtils.deleteFile(picturePath);
                            logger.info("Deleted picture at {}: {}", picturePath, deleted);
                        }
                    } catch (Exception e) {
                        logger.error("Error handling picture at {}: {}", picturePath, e.getMessage());
                        // Continue with other pictures even if one fails
                    }
                }
            }
        }
        
        // Now delete the passdown itself
        passdownRepository.deleteById(id);
        logger.info("Passdown with ID: {} deleted from database", id);
    }

    /**
     * Link a picture from a passdown to an RMA
     *
     * @param picturePath the path of the picture
     * @param rmaId the ID of the RMA to link to
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean linkPictureToRma(String picturePath, Long rmaId) {
        logger.info("Linking picture {} from passdown to RMA {}", picturePath, rmaId);
        
        try {
            // Get all passdowns and find one with the matching picture path
            List<Passdown> allPassdowns = passdownRepository.findAll();
            Optional<Passdown> passdownOpt = Optional.empty();
            
            for (Passdown passdown : allPassdowns) {
                if (passdown.getPicturePaths() != null && passdown.getPicturePaths().contains(picturePath)) {
                    passdownOpt = Optional.of(passdown);
                    break;
                }
            }
            
            if (passdownOpt.isEmpty()) {
                logger.warn("No passdown found with picture path: {}", picturePath);
                return false;
            }
            
            Passdown passdown = passdownOpt.get();
            
            // Get the original filename if available
            String originalFilename = passdown.getPictureNames().get(picturePath);
            if (originalFilename == null) {
                originalFilename = "Passdown_" + passdown.getId() + "_" + System.currentTimeMillis();
                logger.info("No original filename found, using generated name: {}", originalFilename);
            }
            
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
            picture.setFilePath(picturePath);
            picture.setFileName(originalFilename);
            picture.setFileType(getFileTypeFromPath(picturePath));
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
     * Link a picture from a passdown to a tool
     *
     * @param picturePath the path of the picture
     * @param toolId the ID of the tool to link to
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean linkPictureToTool(String picturePath, Long toolId) {
        logger.info("Linking picture {} from passdown to tool {}", picturePath, toolId);
        
        try {
            // Get all passdowns and find one with the matching picture path
            List<Passdown> allPassdowns = passdownRepository.findAll();
            Optional<Passdown> passdownOpt = Optional.empty();
            
            for (Passdown passdown : allPassdowns) {
                if (passdown.getPicturePaths() != null && passdown.getPicturePaths().contains(picturePath)) {
                    passdownOpt = Optional.of(passdown);
                    break;
                }
            }
            
            if (passdownOpt.isEmpty()) {
                logger.warn("No passdown found with picture path: {}", picturePath);
                return false;
            }
            
            Passdown passdown = passdownOpt.get();
            
            // Get the original filename if available
            String originalFilename = passdown.getPictureNames().get(picturePath);
            if (originalFilename == null) {
                originalFilename = "Passdown_" + passdown.getId() + "_" + System.currentTimeMillis();
                logger.info("No original filename found, using generated name: {}", originalFilename);
            }
            
            // Find the tool
            Optional<Tool> toolOpt = toolRepository.findById(toolId);
            if (toolOpt.isEmpty()) {
                logger.warn("Tool with ID {} not found", toolId);
                return false;
            }
            
            Tool tool = toolOpt.get();
            
            // Add the picture to the tool
            tool.getPicturePaths().add(picturePath);
            tool.getPictureNames().put(picturePath, originalFilename);
            
            // Save the tool
            toolRepository.save(tool);
            logger.info("Successfully linked picture to tool {}", toolId);
            
            return true;
        } catch (Exception e) {
            logger.error("Error linking picture to tool: {}", e.getMessage(), e);
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

    /**
     * Gets passdowns associated with a specific tool
     * @param toolId The ID of the tool
     * @return List of passdowns for the specified tool
     */
    public List<Passdown> getPassdownsByToolId(Long toolId) {
        logger.debug("Getting passdowns for tool ID: {}", toolId);
        return passdownRepository.findByToolIdOrderByDateDesc(toolId);
    }
    
    /**
     * OPTIMIZATION: Bulk gets passdowns for multiple tools to avoid N+1 queries
     * @param toolIds The list of tool IDs
     * @return List of passdowns for the specified tools
     */
    public List<Passdown> getPassdownsByToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        logger.debug("Bulk getting passdowns for {} tool IDs", toolIds.size());
        return passdownRepository.findByToolIdInOrderByDateDesc(toolIds);
    }
} 