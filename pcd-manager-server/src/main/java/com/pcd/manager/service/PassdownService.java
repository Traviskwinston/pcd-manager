package com.pcd.manager.service;

import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.repository.PassdownRepository;
import com.pcd.manager.util.UploadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.HashMap;

@Service
public class PassdownService {

    private static final Logger logger = LoggerFactory.getLogger(PassdownService.class);
    private final PassdownRepository passdownRepository;
    private final ToolService toolService;
    private final UploadUtils uploadUtils;

    @Autowired
    public PassdownService(PassdownRepository passdownRepository, ToolService toolService, UploadUtils uploadUtils) {
        this.passdownRepository = passdownRepository;
        this.toolService = toolService;
        this.uploadUtils = uploadUtils;
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
                 selectedTool.getPictureNames().put(newPicturePath, "Passdown: " + originalFilename);
                 
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
     * Special method to safely save a passdown after deleting a picture.
     * This avoids the complex logic in the regular save method.
     * 
     * @param passdown The passdown entity to save
     * @return The saved passdown
     */
    @Transactional
    public Passdown savePassdownForDelete(Passdown passdown) {
        logger.debug("Saving passdown after picture deletion, ID: {}", passdown.getId());
        
        // Pre-save log for debugging
        if (passdown.getPicturePaths() != null) {
            logger.debug("Current picture paths ({}): {}", passdown.getPicturePaths().size(), passdown.getPicturePaths());
        }
        if (passdown.getPictureNames() != null) {
            logger.debug("Current picture names ({}): {}", passdown.getPictureNames().size(), passdown.getPictureNames());
        }
        
        // Simple save bypassing the complex logic in the regular save method
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
} 