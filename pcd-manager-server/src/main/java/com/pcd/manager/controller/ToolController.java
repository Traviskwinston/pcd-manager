package com.pcd.manager.controller;

import com.pcd.manager.model.Location;
import com.pcd.manager.model.Tool;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

@Controller
@RequestMapping("/tools")
public class ToolController {

    private static final Logger logger = LoggerFactory.getLogger(ToolController.class);
    
    private final ToolService toolService;
    private final LocationService locationService;
    
    @Value("${app.upload.dir:${user.home}/uploads}")
    private String uploadDir;

    @Autowired
    public ToolController(ToolService toolService, LocationService locationService) {
        this.toolService = toolService;
        this.locationService = locationService;
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
    public String showTool(@PathVariable Long id, Model model) {
        toolService.getToolById(id).ifPresent(tool -> model.addAttribute("tool", tool));
        
        // Add all tools for the move document/picture dropdowns
        List<Tool> allTools = toolService.getAllTools();
        model.addAttribute("allTools", allTools);
        
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
        
        // Remove from the document collections
        tool.getDocumentPaths().remove(documentPath);
        tool.getDocumentNames().remove(documentPath);
        
        // Delete the actual file
        try {
            Path path = Paths.get(uploadDir + File.separator + documentPath);
            Files.deleteIfExists(path);
            logger.info("Deleted file: {}", path);
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", e.getMessage());
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
        
        // Remove from the picture collections
        tool.getPicturePaths().remove(picturePath);
        tool.getPictureNames().remove(picturePath);
        
        // Delete the actual file
        try {
            Path path = Paths.get(uploadDir + File.separator + picturePath);
            Files.deleteIfExists(path);
            logger.info("Deleted file: {}", path);
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", e.getMessage());
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
} 