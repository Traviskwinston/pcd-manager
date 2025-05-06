package com.pcd.manager.controller;

import com.pcd.manager.model.Location;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.model.RmaDocument;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.User;
import com.pcd.manager.model.Note;
import com.pcd.manager.model.MovingPart;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.LocationService;
import com.pcd.manager.service.RmaService;
import com.pcd.manager.service.PassdownService;
import com.pcd.manager.service.UserService;
import com.pcd.manager.service.NoteService;
import com.pcd.manager.service.MovingPartService;
import com.pcd.manager.util.UploadUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;
import java.security.Principal;

@Controller
@RequestMapping("/tools")
public class ToolController {

    private static final Logger logger = LoggerFactory.getLogger(ToolController.class);
    
    private final ToolService toolService;
    private final LocationService locationService;
    private final RmaService rmaService;
    private final UploadUtils uploadUtils;
    private final PassdownService passdownService;
    private final UserService userService;
    private final NoteService noteService;
    private final MovingPartService movingPartService;
    
    @Value("${app.upload.dir:${user.home}/uploads}")
    private String uploadDir;

    @Autowired
    public ToolController(ToolService toolService, LocationService locationService, RmaService rmaService, UploadUtils uploadUtils, PassdownService passdownService, UserService userService, NoteService noteService, MovingPartService movingPartService) {
        this.toolService = toolService;
        this.locationService = locationService;
        this.rmaService = rmaService;
        this.uploadUtils = uploadUtils;
        this.passdownService = passdownService;
        this.userService = userService;
        this.noteService = noteService;
        this.movingPartService = movingPartService;
    }
    
    @PostConstruct
    public void init() {
        try {
            File uploadDirectory = new File(uploadDir);
            if (!uploadDirectory.exists()) {
                if (uploadDirectory.mkdirs()) {
                    logger.info("Created upload directory: {}", uploadDir);
                } else {
                    logger.error("Failed to create upload directory: {}", uploadDir);
                }
            } else {
                logger.info("Upload directory exists: {}", uploadDir);
            }
            
            // Create subdirectories
            new File(uploadDir + File.separator + "documents").mkdirs();
            new File(uploadDir + File.separator + "pictures").mkdirs();
        } catch (Exception e) {
            logger.error("Error initializing upload directory", e);
        }
    }

    @GetMapping
    public String listTools(Model model) {
        List<Tool> tools = toolService.getAllTools();
        model.addAttribute("tools", tools);
        return "tools/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        Tool tool = new Tool();
        
        // Set default location if available
        locationService.getDefaultLocation().ifPresent(tool::setLocation);
        
        model.addAttribute("tool", tool);
        model.addAttribute("locations", locationService.getAllLocations());
        return "tools/form";
    }

    @GetMapping("/{id}")
    public String showToolDetails(@PathVariable Long id, Model model, Principal principal) {
        toolService.getToolById(id).ifPresent(tool -> {
            model.addAttribute("tool", tool);
            
            // Fetch RMAs associated with this tool
            List<Rma> associatedRmas = rmaService.findRmasByToolId(id);
            model.addAttribute("associatedRmas", associatedRmas);
            logger.info("Found {} RMAs associated with tool ID: {}", associatedRmas.size(), id);
            
            // Fetch Passdowns associated with this tool
            List<Passdown> toolPassdowns = passdownService.getPassdownsByToolId(id);
            model.addAttribute("toolPassdowns", toolPassdowns);
            logger.info("Found {} Passdowns associated with tool ID: {}", toolPassdowns.size(), id);
            
            // Fetch Users with this tool as their active tool
            List<User> usersWithActiveTool = userService.getUsersByActiveTool(id);
            model.addAttribute("usersWithActiveTool", usersWithActiveTool);
            logger.info("Found {} Users with tool ID: {} as active tool", usersWithActiveTool.size(), id);
            
            // Fetch Notes associated with this tool
            List<Note> toolNotes = noteService.getNotesByToolId(id);
            model.addAttribute("toolNotes", toolNotes);
            logger.info("Found {} Notes associated with tool ID: {}", toolNotes.size(), id);
            
            // Get current user and check if assigned to this tool
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                String email = auth.getName();
                userService.getUserByEmail(email).ifPresent(currentUser -> {
                    model.addAttribute("currentUser", currentUser);
                    boolean isCurrentUserAssigned = usersWithActiveTool.stream()
                            .anyMatch(user -> user.getId().equals(currentUser.getId()));
                    model.addAttribute("isCurrentUserAssigned", isCurrentUserAssigned);
                });
            }
        });
        
        // Add all tools for the move document/picture dropdowns
        List<Tool> allTools = toolService.getAllTools();
        model.addAttribute("allTools", allTools);
        
        // Add all RMAs for link functionality
        List<Rma> allRmas = rmaService.getAllRmas();
        model.addAttribute("allRmas", allRmas);
        
        // Add recent passdowns for link functionality
        List<Passdown> recentPassdowns = passdownService.getRecentPassdowns(20);
        model.addAttribute("recentPassdowns", recentPassdowns);
        
        // Get moving parts data
        List<MovingPart> movingParts = movingPartService.getMovingPartsByToolId(id);
        model.addAttribute("movingParts", movingParts);
        
        // Create a new Note object for the note creation form
        model.addAttribute("newNote", new Note());
        
        return "tools/details";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        toolService.getToolById(id).ifPresent(tool -> model.addAttribute("tool", tool));
        model.addAttribute("locations", locationService.getAllLocations());
        return "tools/form";
    }

    @PostMapping
    public String saveTool(
            @ModelAttribute Tool tool,
            @RequestParam(value = "documentFiles", required = false) MultipartFile[] documentFiles,
            @RequestParam(value = "pictureFiles", required = false) MultipartFile[] pictureFiles) {
        
        logger.info("Starting tool save process for tool: {}", tool.getName());
        
        try {
            // Preserve existing documents and pictures when editing
            if (tool.getId() != null) {
                Tool existingTool = toolService.getToolById(tool.getId()).orElse(null);
                if (existingTool != null) {
                    // Keep existing document paths and names
                    Set<String> existingDocPaths = new HashSet<>(existingTool.getDocumentPaths());
                    Map<String, String> existingDocNames = new HashMap<>(existingTool.getDocumentNames());
                    
                    // Keep existing picture paths and names
                    Set<String> existingPicPaths = new HashSet<>(existingTool.getPicturePaths());
                    Map<String, String> existingPicNames = new HashMap<>(existingTool.getPictureNames());
                    
                    // Add back to the tool being saved
                    tool.getDocumentPaths().addAll(existingDocPaths);
                    tool.getDocumentNames().putAll(existingDocNames);
                    tool.getPicturePaths().addAll(existingPicPaths);
                    tool.getPictureNames().putAll(existingPicNames);
                    
                    logger.info("Preserved {} existing documents and {} existing pictures", 
                              existingDocPaths.size(), existingPicPaths.size());
                }
            }
            
            // Set default location if none selected
            if (tool.getLocation() == null) {
                logger.info("No location selected, attempting to set default location");
                locationService.getDefaultLocation().ifPresent(location -> {
                    tool.setLocation(location);
                    logger.info("Default location set to: {}", location.getDisplayName());
                });
            }
            
            // Handle document uploads
            logger.info("Processing document uploads");
            if (documentFiles != null && documentFiles.length > 0) {
                logger.info("Found {} document files to process", documentFiles.length);
                for (MultipartFile file : documentFiles) {
                    if (!file.isEmpty()) {
                        logger.info("Processing document file: {}", file.getOriginalFilename());
                        String filePath = saveUploadedFile(file, "documents");
                        if (filePath != null) {
                            tool.getDocumentPaths().add(filePath);
                            tool.getDocumentNames().put(filePath, file.getOriginalFilename());
                            logger.info("Added document path: {}", filePath);
                        } else {
                            logger.warn("Failed to save document: {}", file.getOriginalFilename());
                        }
                    }
                }
            } else {
                logger.info("No document files to process");
            }
            
            // Handle picture uploads
            logger.info("Processing picture uploads");
            if (pictureFiles != null && pictureFiles.length > 0) {
                logger.info("Found {} picture files to process", pictureFiles.length);
                for (MultipartFile file : pictureFiles) {
                    if (!file.isEmpty()) {
                        logger.info("Processing picture file: {}", file.getOriginalFilename());
                        String filePath = saveUploadedFile(file, "pictures");
                        if (filePath != null) {
                            tool.getPicturePaths().add(filePath);
                            tool.getPictureNames().put(filePath, file.getOriginalFilename());
                            logger.info("Added picture path: {}", filePath);
                        } else {
                            logger.warn("Failed to save picture: {}", file.getOriginalFilename());
                        }
                    }
                }
            } else {
                logger.info("No picture files to process");
            }
            
            logger.info("Calling toolService.saveTool");
            try {
                toolService.saveTool(tool);
                logger.info("Tool saved successfully, redirecting to tool list");
                return "redirect:/tools";
            } catch (Exception e) {
                logger.error("Error in toolService.saveTool: {}", e.getMessage(), e);
                return "redirect:/tools/new?error=savefailed";
            }
        } catch (Exception e) {
            logger.error("Error saving tool: {}", e.getMessage(), e);
            e.printStackTrace();
            // Add the tool back to the model for form redisplay
            return "redirect:/tools/new?error=" + e.getMessage();
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteTool(@PathVariable Long id) {
        toolService.deleteTool(id);
        return "redirect:/tools";
    }
    
    /**
     * Deletes a document from a tool
     */
    @PostMapping("/{id}/documents/delete")
    public String deleteDocument(@PathVariable Long id, @RequestParam String documentPath) {
        logger.info("Deleting document {} from tool {}", documentPath, id);
        
        Tool tool = toolService.getToolById(id).orElse(null);
        if (tool == null) {
            logger.warn("Tool not found: {}", id);
            return "redirect:/tools";
        }
        
        // Check if this document belongs to an RMA
        boolean isRmaDocument = false;
        if (tool.getDocumentNames() != null && tool.getDocumentNames().containsKey(documentPath)) {
            String documentName = tool.getDocumentNames().get(documentPath);
            isRmaDocument = documentName != null && documentName.startsWith("RMA");
        }
        
        // If this is an RMA document, we should find and update the RMA
        if (isRmaDocument) {
            logger.info("This appears to be an RMA-linked document. Searching for associated RMA...");
            
            // Find all RMAs with this tool
            List<Rma> associatedRmas = rmaService.findRmasByToolId(id);
            boolean rmaDocumentRemoved = false;
            
            // Look through each RMA for the document
            for (Rma rma : associatedRmas) {
                if (rma.getDocuments() == null || rma.getDocuments().isEmpty()) {
                    continue;
                }
                
                // Find and remove the document from the RMA
                for (RmaDocument rmaDocument : new ArrayList<>(rma.getDocuments())) {
                    if (documentPath.equals(rmaDocument.getFilePath())) {
                        logger.info("Found matching document in RMA {}. Removing document entity.", rma.getId());
                        
                        try {
                            // Use the RMA service to properly delete the document
                            rmaService.deleteDocument(rmaDocument.getId());
                            rmaDocumentRemoved = true;
                            logger.info("Successfully removed document from RMA {}", rma.getId());
                        } catch (Exception e) {
                            logger.error("Error removing document from RMA {}: {}", rma.getId(), e.getMessage(), e);
                        }
                        break; // Found and processed the document, no need to continue
                    }
                }
                
                if (rmaDocumentRemoved) {
                    break; // Found and removed the document, no need to check other RMAs
                }
            }
            
            if (!rmaDocumentRemoved) {
                logger.warn("Could not find or remove the document from any associated RMA. Proceeding with tool removal only.");
            }
        }
        
        // Remove from the document collections
        tool.getDocumentPaths().remove(documentPath);
        tool.getDocumentNames().remove(documentPath);
        
        // Don't delete the physical file if it was an RMA document (it will be handled by RMA service)
        // Only delete if it wasn't associated with an RMA
        if (!isRmaDocument) {
            // Delete the actual file
            try {
                Path path = Paths.get(uploadDir + File.separator + documentPath);
                Files.deleteIfExists(path);
                logger.info("Deleted file: {}", path);
            } catch (IOException e) {
                logger.error("Failed to delete file: {}", e.getMessage());
            }
        }
        
        // Save the updated tool
        toolService.saveTool(tool);
        
        return "redirect:/tools/" + id;
    }
    
    /**
     * Deletes a picture from a tool
     */
    @PostMapping("/{id}/pictures/delete")
    public String deletePicture(@PathVariable Long id, @RequestParam String picturePath) {
        logger.info("Deleting picture {} from tool {}", picturePath, id);
        
        Tool tool = toolService.getToolById(id).orElse(null);
        if (tool == null) {
            logger.warn("Tool not found: {}", id);
            return "redirect:/tools";
        }
        
        // Check if this picture belongs to an RMA
        boolean isRmaPicture = false;
        if (tool.getPictureNames() != null && tool.getPictureNames().containsKey(picturePath)) {
            String pictureName = tool.getPictureNames().get(picturePath);
            isRmaPicture = pictureName != null && pictureName.startsWith("RMA");
        }
        
        // If this is an RMA picture, we should find and update the RMA
        if (isRmaPicture) {
            logger.info("This appears to be an RMA-linked picture. Searching for associated RMA...");
            
            // Find all RMAs with this tool
            List<Rma> associatedRmas = rmaService.findRmasByToolId(id);
            boolean rmaPictureRemoved = false;
            
            // Look through each RMA for the picture
            for (Rma rma : associatedRmas) {
                if (rma.getPictures() == null || rma.getPictures().isEmpty()) {
                    continue;
                }
                
                // Find and remove the picture from the RMA
                for (RmaPicture rmaPicture : new ArrayList<>(rma.getPictures())) {
                    if (picturePath.equals(rmaPicture.getFilePath())) {
                        logger.info("Found matching picture in RMA {}. Removing picture entity.", rma.getId());
                        
                        try {
                            // Use the RMA service to properly delete the picture
                            rmaService.deletePicture(rmaPicture.getId());
                            rmaPictureRemoved = true;
                            logger.info("Successfully removed picture from RMA {}", rma.getId());
                        } catch (Exception e) {
                            logger.error("Error removing picture from RMA {}: {}", rma.getId(), e.getMessage(), e);
                        }
                        break; // Found and processed the picture, no need to continue
                    }
                }
                
                if (rmaPictureRemoved) {
                    break; // Found and removed the picture, no need to check other RMAs
                }
            }
            
            if (!rmaPictureRemoved) {
                logger.warn("Could not find or remove the picture from any associated RMA. Proceeding with tool removal only.");
            }
        }
        
        // Remove from the picture collections
        tool.getPicturePaths().remove(picturePath);
        tool.getPictureNames().remove(picturePath);
        
        // Don't delete the physical file if it was an RMA picture (it will be handled by RMA service)
        // Only delete if it wasn't associated with an RMA
        if (!isRmaPicture) {
            // Delete the actual file
            try {
                Path path = Paths.get(uploadDir + File.separator + picturePath);
                Files.deleteIfExists(path);
                logger.info("Deleted file: {}", path);
            } catch (IOException e) {
                logger.error("Failed to delete file: {}", e.getMessage());
            }
        }
        
        // Save the updated tool
        toolService.saveTool(tool);
        
        return "redirect:/tools/" + id;
    }
    
    /**
     * Moves a document from one tool to another
     */
    @PostMapping("/{id}/documents/move")
    public String moveDocument(
            @PathVariable Long id, 
            @RequestParam String documentPath, 
            @RequestParam Long destinationToolId) {
        
        logger.info("Moving document {} from tool {} to tool {}", documentPath, id, destinationToolId);
        
        Tool sourceTool = toolService.getToolById(id).orElse(null);
        Tool destinationTool = toolService.getToolById(destinationToolId).orElse(null);
        
        if (sourceTool == null || destinationTool == null) {
            logger.warn("Source or destination tool not found");
            return "redirect:/tools";
        }
        
        // Check if the document exists in the source tool
        if (!sourceTool.getDocumentPaths().contains(documentPath)) {
            logger.warn("Document {} not found in tool {}", documentPath, id);
            return "redirect:/tools/" + id;
        }
        
        // Get the original filename if available
        String originalFilename = sourceTool.getDocumentNames().get(documentPath);
        
        // Remove from source tool
        sourceTool.getDocumentPaths().remove(documentPath);
        sourceTool.getDocumentNames().remove(documentPath);
        
        // Add to destination tool
        destinationTool.getDocumentPaths().add(documentPath);
        if (originalFilename != null) {
            destinationTool.getDocumentNames().put(documentPath, originalFilename);
        }
        
        // Save both tools
        toolService.saveTool(sourceTool);
        toolService.saveTool(destinationTool);
        
        return "redirect:/tools/" + id;
    }
    
    /**
     * Moves a picture from one tool to another
     */
    @PostMapping("/{id}/pictures/move")
    public String movePicture(
            @PathVariable Long id, 
            @RequestParam String picturePath, 
            @RequestParam Long destinationToolId) {
        
        logger.info("Moving picture {} from tool {} to tool {}", picturePath, id, destinationToolId);
        
        Tool sourceTool = toolService.getToolById(id).orElse(null);
        Tool destinationTool = toolService.getToolById(destinationToolId).orElse(null);
        
        if (sourceTool == null || destinationTool == null) {
            logger.warn("Source or destination tool not found");
            return "redirect:/tools";
        }
        
        // Check if the picture exists in the source tool
        if (!sourceTool.getPicturePaths().contains(picturePath)) {
            logger.warn("Picture {} not found in tool {}", picturePath, id);
            return "redirect:/tools/" + id;
        }
        
        // Get the original filename if available
        String originalFilename = sourceTool.getPictureNames().get(picturePath);
        
        // Remove from source tool
        sourceTool.getPicturePaths().remove(picturePath);
        sourceTool.getPictureNames().remove(picturePath);
        
        // Add to destination tool
        destinationTool.getPicturePaths().add(picturePath);
        if (originalFilename != null) {
            destinationTool.getPictureNames().put(picturePath, originalFilename);
        }
        
        // Save both tools
        toolService.saveTool(sourceTool);
        toolService.saveTool(destinationTool);
        
        return "redirect:/tools/" + id;
    }
    
    /**
     * Saves uploaded file to the server's file system
     * 
     * @param file The file to save
     * @param subdirectory The subdirectory under upload dir
     * @return The path where the file was saved, or null if it failed
     */
    private String saveUploadedFile(MultipartFile file, String subdirectory) {
        if (file.isEmpty()) {
            return null;
        }
        
        try {
            // Create directories if they don't exist
            File directory = new File(uploadDir + File.separator + subdirectory);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            // Generate a unique filename
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                return null;
            }
            
            String extension = "";
            int lastDotIndex = originalFilename.lastIndexOf(".");
            if (lastDotIndex > 0) {
                extension = originalFilename.substring(lastDotIndex);
            }
            
            String newFilename = UUID.randomUUID().toString() + extension;
            String relativePath = subdirectory + File.separator + newFilename;
            
            // Save the file
            Path path = Paths.get(uploadDir + File.separator + relativePath);
            Files.write(path, file.getBytes());
            
            return relativePath;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Link a file (document/picture) to another entity (RMA/Passdown)
     */
    @PostMapping("/file/link")
    public String linkFile(@RequestParam String filePath,
                           @RequestParam(required = false) String fileName,
                           @RequestParam Long toolId,
                           @RequestParam String linkType,
                           @RequestParam String fileType,
                           @RequestParam Long targetId) {
        
        logger.info("Linking {} {} from Tool {} to {} {}", fileType, filePath, toolId, linkType, targetId);
        
        boolean success = false;
        
        try {
            if ("rma".equals(linkType)) {
                // Link to RMA
                if ("document".equals(fileType)) {
                    // Logic to link document to RMA
                    success = toolService.linkDocumentToRma(filePath, fileName, targetId);
                } else if ("picture".equals(fileType)) {
                    // Logic to link picture to RMA
                    success = toolService.linkPictureToRma(filePath, fileName, targetId);
                }
            } else if ("passdown".equals(linkType)) {
                // Link to Passdown
                if ("document".equals(fileType)) {
                    // Logic to link document to Passdown
                    success = toolService.linkDocumentToPassdown(filePath, fileName, targetId);
                } else if ("picture".equals(fileType)) {
                    // Logic to link picture to Passdown
                    success = toolService.linkPictureToPassdown(filePath, fileName, targetId);
                }
            }
            
            if (success) {
                logger.info("Successfully linked {} to {} {}", fileType, linkType, targetId);
            } else {
                logger.warn("Failed to link {} to {} {}", fileType, linkType, targetId);
            }
        } catch (Exception e) {
            logger.error("Error linking {} to {} {}: {}", fileType, linkType, targetId, e.getMessage(), e);
        }
        
        // Redirect back to tool details
        return "redirect:/tools/" + toolId;
    }

    /**
     * Assigns this tool to the current user as their active tool
     */
    @PostMapping("/{id}/assign")
    public String assignToolToCurrentUser(@PathVariable Long id) {
        logger.info("Assigning tool ID: {} to current user", id);
        
        // Get current authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String email = auth.getName();
            Optional<User> currentUserOpt = userService.getUserByEmail(email);
            Optional<Tool> toolOpt = toolService.getToolById(id);
            
            if (currentUserOpt.isPresent() && toolOpt.isPresent()) {
                User currentUser = currentUserOpt.get();
                Tool tool = toolOpt.get();
                
                // Set this tool as the user's active tool
                currentUser.setActiveTool(tool);
                userService.updateUser(currentUser);

                // Also add the user to the tool's technician list for dashboard tracking
                tool.getCurrentTechnicians().add(currentUser);
                toolService.saveTool(tool);
                
                logger.info("Successfully assigned tool {} to user {}", tool.getName(), currentUser.getName());
            } else {
                logger.warn("Failed to assign tool. User or tool not found.");
            }
        } else {
            logger.warn("No authenticated user found when trying to assign tool");
        }
        
        return "redirect:/tools/" + id;
    }
    
    /**
     * Unassigns the current user from their active tool
     */
    @PostMapping("/{id}/unassign")
    public String unassignToolFromCurrentUser(@PathVariable Long id) {
        logger.info("Unassigning current user from tool ID: {}", id);
        
        // Get current authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String email = auth.getName();
            Optional<User> currentUserOpt = userService.getUserByEmail(email);
            Optional<Tool> toolOpt = toolService.getToolById(id);
            
            if (currentUserOpt.isPresent() && toolOpt.isPresent()) {
                User currentUser = currentUserOpt.get();
                Tool tool = toolOpt.get();
                
                // Check if user is assigned to this specific tool
                if (currentUser.getActiveTool() != null && 
                    currentUser.getActiveTool().getId().equals(tool.getId())) {
                    
                    // Remove the tool assignment from user
                    currentUser.setActiveTool(null);
                    userService.updateUser(currentUser);

                    // Also remove the user from the tool's technician list
                    tool.getCurrentTechnicians().remove(currentUser);
                    toolService.saveTool(tool);
                    
                    logger.info("Successfully unassigned user {} from tool {}", 
                               currentUser.getName(), tool.getName());
                } else {
                    logger.warn("User {} is not assigned to tool {}", 
                               currentUser.getName(), tool.getName());
                }
            } else {
                logger.warn("Failed to unassign tool. User or tool not found.");
            }
        } else {
            logger.warn("No authenticated user found when trying to unassign tool");
        }
        
        return "redirect:/tools/" + id;
    }

    /**
     * Handles adding a new note to a tool
     */
    @PostMapping("/{id}/notes/add")
    public String addNote(@PathVariable Long id, 
                         @RequestParam("content") String content,
                         RedirectAttributes redirectAttributes) {
        logger.info("Adding note to tool ID: {}", id);
        
        try {
            // Get the tool
            Tool tool = toolService.getToolById(id)
                .orElseThrow(() -> new RuntimeException("Tool not found with ID: " + id));
                
            // Get current user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
                throw new RuntimeException("User must be authenticated to add notes");
            }
            
            String email = auth.getName();
            User currentUser = userService.getUserByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
                
            // Create and save the note
            Note note = noteService.createNote(content, tool, currentUser);
            logger.info("Successfully added note ID: {} to tool ID: {}", note.getId(), id);
            
            redirectAttributes.addFlashAttribute("message", "Note added successfully");
        } catch (Exception e) {
            logger.error("Error adding note to tool: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error adding note: " + e.getMessage());
        }
        
        return "redirect:/tools/" + id;
    }
    
    /**
     * Handles deleting a note
     */
    @PostMapping("/{toolId}/notes/{noteId}/delete")
    public String deleteNote(@PathVariable Long toolId, 
                           @PathVariable Long noteId,
                           RedirectAttributes redirectAttributes) {
        logger.info("Deleting note ID: {} from tool ID: {}", noteId, toolId);
        
        try {
            // Get the note
            Note note = noteService.getNoteById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found with ID: " + noteId));
                
            // Verify the note belongs to the tool
            if (!note.getTool().getId().equals(toolId)) {
                throw new RuntimeException("Note does not belong to the specified tool");
            }
            
            // Get current user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
                throw new RuntimeException("User must be authenticated to delete notes");
            }
            
            String email = auth.getName();
            User currentUser = userService.getUserByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
                
            // Only allow the note creator or admins to delete notes
            if (!note.getUser().getId().equals(currentUser.getId()) && !"ADMIN".equals(currentUser.getRole())) {
                throw new RuntimeException("You can only delete your own notes");
            }
            
            // Delete the note
            noteService.deleteNote(noteId);
            logger.info("Successfully deleted note ID: {} from tool ID: {}", noteId, toolId);
            
            redirectAttributes.addFlashAttribute("message", "Note deleted successfully");
        } catch (Exception e) {
            logger.error("Error deleting note: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error deleting note: " + e.getMessage());
        }
        
        return "redirect:/tools/" + toolId;
    }
} 