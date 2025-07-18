package com.pcd.manager.controller;

import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.model.Rma;
import com.pcd.manager.service.PassdownService;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.UserService;
import com.pcd.manager.service.RmaService;
import com.pcd.manager.util.UploadUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

@Controller
@RequestMapping("/passdown")
public class PassdownController {

    private static final Logger logger = LoggerFactory.getLogger(PassdownController.class);

    private final PassdownService passdownService;
    private final UserService userService;
    private final ToolService toolService;
    private final RmaService rmaService;
    private final UploadUtils uploadUtils;

    @Value("${app.upload.dir:${user.home}/uploads}")
    private String uploadDir;

    @Autowired
    public PassdownController(PassdownService passdownService, UserService userService, ToolService toolService, RmaService rmaService, UploadUtils uploadUtils) {
        this.passdownService = passdownService;
        this.userService = userService;
        this.toolService = toolService;
        this.rmaService = rmaService;
        this.uploadUtils = uploadUtils;
    }

    @PostConstruct
    public void init() {
        // Initialize upload directories
        try {
            uploadUtils.initializeDirectories();
            logger.info("Initialized upload directories");
        } catch (Exception e) {
            logger.error("Failed to initialize upload directories", e);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String listPassdowns(Model model,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        List<Passdown> passdowns;
        
        if (startDate != null && endDate != null) {
            passdowns = passdownService.getPassdownsByDateRange(startDate, endDate);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
        } else if (startDate != null) {
            passdowns = passdownService.getPassdownsByDate(startDate);
            model.addAttribute("startDate", startDate);
        } else {
            // Default to all passdowns when no date parameters are provided
            passdowns = passdownService.getAllPassdowns();
        }
        
        // Extract unique user names and tool names for filters (with safe lazy loading handling)
        logger.info("Processing {} passdowns for filter extraction", passdowns.size());
        List<String> passdownUsers = new ArrayList<>();
        List<String> passdownTools = new ArrayList<>();
        
        for (Passdown p : passdowns) {
            try {
                if (p.getUser() != null && p.getUser().getName() != null) {
                    String userName = p.getUser().getName();
                    if (!passdownUsers.contains(userName)) {
                        passdownUsers.add(userName);
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not load user name for passdown {}: {}", p.getId(), e.getMessage());
            }
            
            try {
                if (p.getTool() != null && p.getTool().getName() != null) {
                    String toolName = p.getTool().getName();
                    if (!passdownTools.contains(toolName)) {
                        passdownTools.add(toolName);
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not load tool name for passdown {}: {}", p.getId(), e.getMessage());
            }
        }
        
        // Sort the lists
        passdownUsers.sort(String::compareToIgnoreCase);
        passdownTools.sort(String::compareToIgnoreCase);
        
        model.addAttribute("passdownUsers", passdownUsers);
        model.addAttribute("passdownTools", passdownTools);
        model.addAttribute("passdowns", passdowns);
        return "passdown/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        Passdown passdown = new Passdown();
        
        // Get current user's active tool (if any)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String email = auth.getName();
            userService.getUserByEmail(email).ifPresent(currentUser -> {
                if (currentUser.getActiveTool() != null) {
                    // Set the activeTool as the default tool for the passdown
                    passdown.setTool(currentUser.getActiveTool());
                    logger.info("Autofilled tool: {} for user: {}", 
                        currentUser.getActiveTool().getName(), currentUser.getName());
                }
            });
        }
        
        model.addAttribute("passdown", passdown);
        model.addAttribute("tools", toolService.getAllTools());
        model.addAttribute("today", LocalDate.now());
        return "passdown/form";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        Optional<Passdown> passdownOpt = passdownService.getPassdownById(id);
        
        if (passdownOpt.isPresent()) {
            Passdown passdown = passdownOpt.get();
            model.addAttribute("passdown", passdown);
            model.addAttribute("title", "Passdown Details");
            
            // Add all tools for the linking functionality
            List<Tool> allTools = toolService.getAllTools();
            model.addAttribute("allTools", allTools);
            
            // Add all RMAs for linking functionality
            List<Rma> allRmas = rmaService.getAllRmas();
            model.addAttribute("allRmas", allRmas);
        } else {
            return "redirect:/passdown";
        }
        
        return "passdown/view";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Passdown passdown = passdownService.getPassdownByIdWithDetails(id)
            .orElseThrow(() -> new RuntimeException("Passdown not found with id: " + id));
        
        // If the passdown doesn't have a tool set, get the current user's active tool (if any)
        if (passdown.getTool() == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                String email = auth.getName();
                userService.getUserByEmail(email).ifPresent(currentUser -> {
                    if (currentUser.getActiveTool() != null) {
                        // Set the activeTool as the default tool for the passdown
                        passdown.setTool(currentUser.getActiveTool());
                        logger.info("Autofilled tool: {} for existing passdown ID: {}", 
                            currentUser.getActiveTool().getName(), passdown.getId());
                    }
                });
            }
        }
        
        model.addAttribute("passdown", passdown);
        model.addAttribute("tools", toolService.getAllTools());
        model.addAttribute("today", LocalDate.now());
        return "passdown/form";
    }

    @PostMapping
    @Transactional
    public String savePassdown(
            @ModelAttribute("passdown") Passdown passdownFormData,
            @RequestParam(value = "pictureFile", required = false) MultipartFile pictureFile,
            RedirectAttributes redirectAttributes) {

        logger.info("Starting passdown save process. ID: {}, Has picture: {}", 
                passdownFormData.getId(), 
                (pictureFile != null && !pictureFile.isEmpty()));

        // Save file first before database transaction
        String newPicturePath = null;
        String originalFilename = null;
        
        try {
            // Get current user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            User currentUser = userService.getUserByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
                    
            // Process picture upload if present
            if (pictureFile != null && !pictureFile.isEmpty()) {
                logger.info("Processing picture file: {} (size: {})", 
                        pictureFile.getOriginalFilename(),
                        pictureFile.getSize());
                
                try {
                    newPicturePath = uploadUtils.saveFile(pictureFile, "pictures"); 
                    if (newPicturePath != null) {
                        originalFilename = pictureFile.getOriginalFilename();
                        logger.info("Picture file saved to path: {}", newPicturePath);
                    } else {
                        logger.warn("Failed to save uploaded picture file: {}", pictureFile.getOriginalFilename());
                        redirectAttributes.addFlashAttribute("error", "Failed to save the uploaded picture");
                        return "redirect:/passdown/" + (passdownFormData.getId() != null ? passdownFormData.getId() + "/edit" : "new");
                    }
                } catch (IOException e) {
                    logger.error("Error saving uploaded picture file: {}", e.getMessage(), e);
                    redirectAttributes.addFlashAttribute("error", "Error saving picture: " + e.getMessage());
                    return "redirect:/passdown/" + (passdownFormData.getId() != null ? passdownFormData.getId() + "/edit" : "new");
                }
            }

            // Save passdown with separate transaction
            Passdown savedPassdown = passdownService.savePassdown(passdownFormData, currentUser, newPicturePath, originalFilename);
            logger.info("Successfully saved passdown ID: {}", savedPassdown.getId());
            redirectAttributes.addFlashAttribute("message", "Passdown saved successfully");
            return "redirect:/passdown";
            
        } catch (Exception e) {
            logger.error("Error saving passdown: {}", e.getMessage(), e);
            
            // Delete the uploaded file if there was a database error
            if (newPicturePath != null) {
                try {
                    boolean deleted = uploadUtils.deleteFile(newPicturePath);
                    logger.info("Deleted orphaned picture due to save error: {} - Result: {}", newPicturePath, deleted);
                } catch (Exception ex) {
                    logger.error("Error cleaning up orphaned picture: {}", ex.getMessage());
                }
            }
            
            redirectAttributes.addFlashAttribute("error", "Error saving passdown: " + e.getMessage());
            return "redirect:/passdown/" + (passdownFormData.getId() != null ? passdownFormData.getId() + "/edit" : "new");
        }
    }

    @PostMapping("/{id}/pictures/delete")
    @Transactional
    public String deletePassdownPicture(@PathVariable Long id, @RequestParam("picturePath") String picturePath, RedirectAttributes redirectAttributes) {
        try {
            logger.info("Starting picture deletion for passdown ID: {}, picture path: {}", id, picturePath);
            Optional<Passdown> passdownOpt = passdownService.getPassdownByIdWithDetails(id);
            
            if (!passdownOpt.isPresent()) {
                logger.error("Passdown not found with id: {}", id);
                redirectAttributes.addFlashAttribute("error", "Passdown not found");
                return "redirect:/passdown";
            }
            
            Passdown passdown = passdownOpt.get();
            
            // Log current state of the passdown
            logger.info("Current passdown state - ID: {}, Comment: {}, Date: {}, Tool: {}", 
                passdown.getId(), 
                passdown.getComment() != null ? passdown.getComment().substring(0, Math.min(20, passdown.getComment().length())) + "..." : "null",
                passdown.getDate(),
                passdown.getTool() != null ? "Tool ID: " + passdown.getTool().getId() + ", Name: " + passdown.getTool().getName() : "null");
            
            logger.info("Picture collections - Paths: {}, Names: {}", 
                passdown.getPicturePaths() != null ? passdown.getPicturePaths().size() : "null", 
                passdown.getPictureNames() != null ? passdown.getPictureNames().size() : "null");
            
            boolean pictureRemoved = false;
            boolean pictureNameRemoved = false;
            
            // 1. Make a defensive copy of the picture path
            String picPathToDelete = picturePath;
            
            // 2. Remove from collections
            if (passdown.getPicturePaths() != null) {
                pictureRemoved = passdown.getPicturePaths().remove(picPathToDelete);
                logger.info("Removed from picturePaths: {}", pictureRemoved);
            }
            
            if (passdown.getPictureNames() != null) {
                String originalName = passdown.getPictureNames().remove(picPathToDelete);
                pictureNameRemoved = (originalName != null);
                logger.info("Removed from pictureNames: {}, original name: {}", pictureNameRemoved, originalName);
            }
            
            // 3. Preserve the tool association and other important fields
            Tool tool = passdown.getTool();
            if (tool != null) {
                logger.info("Preserving tool - ID: {}, Name: {}", tool.getId(), tool.getName());
            } else {
                logger.warn("No tool associated with this passdown BEFORE service call");
            }
            
            User user = passdown.getUser();
            if (user != null) {
                logger.info("Preserving user - ID: {}, Name: {}", user.getId(), user.getName());
            } else {
                logger.warn("No user associated with this passdown BEFORE service call");
            }
            
            // Log the state *immediately* before calling savePassdown
            logger.info("Passdown state BEFORE calling savePassdown - ID: {}, Tool: {}, Comment: {}, Pictures: {}", 
                passdown.getId(), 
                (passdown.getTool() != null ? "ID:" + passdown.getTool().getId() : "null"), 
                passdown.getComment() != null ? passdown.getComment().substring(0, Math.min(20, passdown.getComment().length())) + "..." : "null",
                passdown.getPicturePaths() != null ? passdown.getPicturePaths().size() : "null");
            
            // 4. Save entity first to update database (using the full savePassdown method to preserve all associations)
            Passdown savedPassdown = passdownService.savePassdown(passdown, user, null, null);
            logger.info("Passdown saved after picture removal. Updated state - Tool: {}", 
                savedPassdown.getTool() != null ? "Tool ID: " + savedPassdown.getTool().getId() + ", Name: " + savedPassdown.getTool().getName() : "null");
            
            // 5. Now delete the file
            if (picPathToDelete != null && !picPathToDelete.isEmpty()) {
                try {
                    logger.info("Checking if picture is associated with a tool before deletion");
                    // Don't delete the physical file if it's still being used in a tool
                    boolean shouldDeletePhysicalFile = true;
                    
                    // Check if any tool is using this picture
                    if (tool != null) {
                        Tool fullTool = toolService.getToolById(tool.getId()).orElse(null);
                        if (fullTool != null && fullTool.getPicturePaths() != null && 
                            fullTool.getPicturePaths().contains(picPathToDelete)) {
                            logger.info("Picture is still in use by the associated tool (ID: {}), not deleting physical file", 
                                fullTool.getId());
                            shouldDeletePhysicalFile = false;
                        }
                    }
                    
                    if (shouldDeletePhysicalFile) {
                        logger.info("Attempting to delete physical file: {}", picPathToDelete);
                        
                        // Get the original path from the database for logging
                        String originalPath = picPathToDelete;
                        logger.info("Original path from database: {}", originalPath);
                        
                        // Try to delete the file
                        boolean fileDeleted = uploadUtils.deleteFile(picPathToDelete);
                        logger.info("Physical file deletion result: {}", fileDeleted);
                        
                        if (fileDeleted) {
                            logger.info("Successfully deleted physical file");
                        } else {
                            logger.warn("Failed to delete physical file: {}. Database will still be updated.", picPathToDelete);
                            // Even though file deletion failed, we'll still return success since the database was updated
                        }
                    } else {
                        logger.info("Skipping physical file deletion as it's still used by a tool");
                    }
                } catch (Exception e) {
                    logger.error("Error deleting physical file: {}", e.getMessage(), e);
                    // Continue even if physical deletion fails
                }
            } else {
                logger.warn("No picture path to delete");
            }
            
            redirectAttributes.addFlashAttribute("message", "Picture deleted successfully");
            return "redirect:/passdown/" + id + "/edit";
        } catch (Exception e) {
            logger.error("Error deleting picture", e);
            redirectAttributes.addFlashAttribute("error", "Error deleting picture: " + e.getMessage());
            return "redirect:/passdown";
        }
    }

    @PostMapping("/{id}/update")
    @Transactional
    public String editPassdown(@PathVariable Long id, 
                              @RequestParam("comment") String comment,
                              @RequestParam("date") String date,
                              RedirectAttributes redirectAttributes) {
        try {
            logger.info("Editing passdown with ID: {}", id);
            
            Optional<Passdown> passdownOpt = passdownService.getPassdownByIdWithDetails(id);
            if (passdownOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Passdown not found");
                return "redirect:/passdown";
            }
            
            Passdown passdown = passdownOpt.get();
            passdown.setComment(comment);
            
            // Parse and set the date
            try {
                passdown.setDate(java.time.LocalDate.parse(date));
            } catch (Exception e) {
                logger.error("Error parsing date: {}", e.getMessage());
                redirectAttributes.addFlashAttribute("error", "Invalid date format");
                return "redirect:/tools/" + (passdown.getTool() != null ? passdown.getTool().getId() : "");
            }
            
            // Get current user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            User currentUser = userService.getUserByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Save the updated passdown
            passdownService.savePassdown(passdown, currentUser, null, null);
            logger.info("Successfully updated passdown ID: {}", id);
            redirectAttributes.addFlashAttribute("message", "Passdown updated successfully");
            
        } catch (Exception e) {
            logger.error("Error editing passdown: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error editing passdown: " + e.getMessage());
        }
        
        // Redirect back to the tool details page (assuming passdown is from a tool)
        Optional<Passdown> passdownOpt = passdownService.getPassdownByIdWithDetails(id);
        if (passdownOpt.isPresent() && passdownOpt.get().getTool() != null) {
            return "redirect:/tools/" + passdownOpt.get().getTool().getId();
        }
        return "redirect:/passdown";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String deletePassdown(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            logger.info("Deleting passdown with ID: {}", id);
            
            // Get the passdown with pictures before deleting
            Optional<Passdown> passdownOpt = passdownService.getPassdownByIdWithDetails(id);
            
            if (passdownOpt.isPresent()) {
                Passdown passdown = passdownOpt.get();
                Set<String> picturesToDelete = new HashSet<>(passdown.getPicturePaths());
                Tool associatedTool = passdown.getTool();
                
                // Delete from database first
                passdownService.deletePassdown(id);
                logger.info("Passdown with ID: {} deleted from database", id);
                
                // Then delete files
                if (!picturesToDelete.isEmpty()) {
                    logger.info("Checking {} pictures for passdown ID: {}", picturesToDelete.size(), id);
                    
                    for (String picturePath : picturesToDelete) {
                        try {
                            boolean shouldDeletePhysicalFile = true;
                            
                            // Check if the picture is being used by the associated tool
                            if (associatedTool != null) {
                                Tool fullTool = toolService.getToolById(associatedTool.getId()).orElse(null);
                                if (fullTool != null && fullTool.getPicturePaths() != null && 
                                    fullTool.getPicturePaths().contains(picturePath)) {
                                    logger.info("Picture {} is still in use by the associated tool (ID: {}), not deleting physical file", 
                                        picturePath, fullTool.getId());
                                    shouldDeletePhysicalFile = false;
                                }
                            }
                            
                            if (shouldDeletePhysicalFile) {
                                boolean deleted = uploadUtils.deleteFile(picturePath);
                                logger.info("Deleted picture at {}: {}", picturePath, deleted);
                            } else {
                                logger.info("Skipping deletion of picture {} as it's still used by a tool", picturePath);
                            }
                        } catch (Exception e) {
                            logger.error("Error handling picture at {}: {}", picturePath, e.getMessage());
                        }
                    }
                }
                
                redirectAttributes.addFlashAttribute("message", "Passdown deleted successfully");
            } else {
                logger.warn("Passdown with ID: {} not found", id);
                redirectAttributes.addFlashAttribute("error", "Passdown not found");
            }
            
            return "redirect:/passdown";
        } catch (Exception e) {
            logger.error("Error deleting passdown: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error deleting passdown: " + e.getMessage());
            return "redirect:/passdown";
        }
    }

    /**
     * Link a file (document/picture) to another entity (RMA/Tool)
     */
    @PostMapping("/file/link")
    public String linkFile(@RequestParam String filePath,
                          @RequestParam Long passdownId,
                          @RequestParam String linkType,
                          @RequestParam Long targetId) {
        
        logger.info("Linking picture {} from Passdown {} to {} {}", filePath, passdownId, linkType, targetId);
        
        boolean success = false;
        
        try {
            if ("rma".equals(linkType)) {
                // Link to RMA
                success = passdownService.linkPictureToRma(filePath, targetId);
            } else if ("tool".equals(linkType)) {
                // Link to Tool
                success = passdownService.linkPictureToTool(filePath, targetId);
            }
            
            if (success) {
                logger.info("Successfully linked picture to {} {}", linkType, targetId);
            } else {
                logger.warn("Failed to link picture to {} {}", linkType, targetId);
            }
        } catch (Exception e) {
            logger.error("Error linking picture to {} {}: {}", linkType, targetId, e.getMessage(), e);
        }
        
        // Redirect back to passdown details
        return "redirect:/passdown/" + passdownId;
    }
} 