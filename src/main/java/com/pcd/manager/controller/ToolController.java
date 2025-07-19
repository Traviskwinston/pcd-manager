package com.pcd.manager.controller;

import com.pcd.manager.model.Location;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.model.RmaDocument;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.User;
import com.pcd.manager.model.Note;
import com.pcd.manager.model.ToolComment;
import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.model.MovingPart;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.repository.PassdownRepository;
import com.pcd.manager.repository.ToolCommentRepository;
import com.pcd.manager.repository.TrackTrendRepository;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.LocationService;
import com.pcd.manager.service.RmaService;
import com.pcd.manager.service.PassdownService;
import com.pcd.manager.service.UserService;
import com.pcd.manager.service.TrackTrendService;
import com.pcd.manager.service.AsyncDataService;
import com.pcd.manager.service.MovingPartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.format.annotation.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.File;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.time.LocalDate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import java.io.InputStream;

@Controller
@RequestMapping("/tools")
public class ToolController {

    private static final Logger logger = LoggerFactory.getLogger(ToolController.class);
    
    private final ToolService toolService;
    private final ToolRepository toolRepository;
    private final LocationService locationService;
    private final RmaService rmaService;
    private final UserService userService;
    private final TrackTrendService trackTrendService;
    private final PassdownService passdownService;
    private final RmaRepository rmaRepository;
    private final PassdownRepository passdownRepository;
    private final ToolCommentRepository toolCommentRepository;
    private final TrackTrendRepository trackTrendRepository;
    private final AsyncDataService asyncDataService;
    private final MovingPartService movingPartService;
    
    @Value("${app.upload.dir:${user.home}/uploads}")
    private String uploadDir;

    @Autowired
    public ToolController(ToolService toolService, ToolRepository toolRepository, LocationService locationService, RmaService rmaService, UserService userService, TrackTrendService trackTrendService, PassdownService passdownService, RmaRepository rmaRepository, PassdownRepository passdownRepository, ToolCommentRepository toolCommentRepository, TrackTrendRepository trackTrendRepository, AsyncDataService asyncDataService, MovingPartService movingPartService) {
        this.toolService = toolService;
        this.toolRepository = toolRepository;
        this.locationService = locationService;
        this.rmaService = rmaService;
        this.userService = userService;
        this.trackTrendService = trackTrendService;
        this.passdownService = passdownService;
        this.rmaRepository = rmaRepository;
        this.passdownRepository = passdownRepository;
        this.toolCommentRepository = toolCommentRepository;
        this.trackTrendRepository = trackTrendRepository;
        this.asyncDataService = asyncDataService;
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
        logger.info("=== LOADING TOOLS LIST PAGE (ULTRA-OPTIMIZED) ===");
        long startTime = System.currentTimeMillis();
        
        // Use ultra-lightweight query for list view - only load essential fields as Object[]
        List<Object[]> toolData = toolRepository.findAllForAsyncListView();
        logger.info("Loaded {} tools for ultra-optimized list view", toolData.size());
        
        if (toolData.isEmpty()) {
            model.addAttribute("tools", new ArrayList<>());
            model.addAttribute("toolRmasMap", new HashMap<>());
            model.addAttribute("toolPassdownsMap", new HashMap<>());
            model.addAttribute("toolCommentsMap", new HashMap<>());
            model.addAttribute("toolTrackTrendsMap", new HashMap<>());
            return "tools/list";
        }
        
        // Build lightweight Tool objects from the raw data
        List<Tool> allTools = new ArrayList<>();
        List<Long> toolIds = new ArrayList<>();
        
        for (Object[] row : toolData) {
            Tool tool = new Tool();
            tool.setId((Long) row[0]);
            tool.setName((String) row[1]);
            tool.setSecondaryName((String) row[2]);
            tool.setToolType((Tool.ToolType) row[3]);
            tool.setSerialNumber1((String) row[4]);
            tool.setSerialNumber2((String) row[5]);
            tool.setModel1((String) row[6]);
            tool.setModel2((String) row[7]);
            // Skip the stored status - we'll calculate it dynamically
            // tool.setStatus((Tool.ToolStatus) row[8]);
            
            // Create minimal location object if location exists
            Long locationId = (Long) row[9];
            String locationName = (String) row[10];
            if (locationId != null && locationId > 0) {
                Location location = new Location();
                location.setId(locationId);
                location.setName(locationName);
                tool.setLocation(location);
            }
            
            // Set checklist date fields for status popover (completion status is determined by helper methods)
            tool.setCommissionDate((java.time.LocalDate) row[11]);
            tool.setPreSl1Date((java.time.LocalDate) row[12]);
            tool.setSl1Date((java.time.LocalDate) row[13]);
            tool.setMechanicalPreSl1Date((java.time.LocalDate) row[14]);
            tool.setMechanicalPostSl1Date((java.time.LocalDate) row[15]);
            tool.setSpecificInputFunctionalityDate((java.time.LocalDate) row[16]);
            tool.setModesOfOperationDate((java.time.LocalDate) row[17]);
            tool.setSpecificSoosDate((java.time.LocalDate) row[18]);
            tool.setFieldServiceReportDate((java.time.LocalDate) row[19]);
            tool.setCertificateOfApprovalDate((java.time.LocalDate) row[20]);
            tool.setTurnedOverToCustomerDate((java.time.LocalDate) row[21]);
            tool.setStartUpSl03Date((java.time.LocalDate) row[22]);
            
            // Set the calculated status based on checklist completion
            tool.setStatus(tool.getCalculatedStatus());
            
            allTools.add(tool);
            toolIds.add(tool.getId());
        }
        
        // Load technician assignments separately
        List<Object[]> technicianData = toolRepository.findTechniciansByToolIds(toolIds);
        Map<Long, List<User>> toolTechniciansMap = new HashMap<>();
        
        for (Object[] row : technicianData) {
            Long toolId = (Long) row[0];
            Long userId = (Long) row[1];
            String userName = (String) row[2];
            
            User user = new User();
            user.setId(userId);
            user.setName(userName);
            
            toolTechniciansMap.computeIfAbsent(toolId, k -> new ArrayList<>()).add(user);
        }
        
        // Assign technicians to tools
        for (Tool tool : allTools) {
            List<User> technicians = toolTechniciansMap.getOrDefault(tool.getId(), new ArrayList<>());
            tool.setCurrentTechnicians(new HashSet<>(technicians));
        }
        
        // Initialize all maps to ensure they're never null
        Map<Long, List<Map<String, Object>>> toolRmasMap = new HashMap<>();
        Map<Long, List<Map<String, Object>>> toolPassdownsMap = new HashMap<>();
        Map<Long, List<Map<String, Object>>> toolCommentsMap = new HashMap<>();
        Map<Long, List<Map<String, Object>>> toolTrackTrendsMap = new HashMap<>();
        
        try {
            // ASYNC OPTIMIZATION: Load all related data in parallel instead of sequentially
            logger.info("Starting parallel async data loading for {} tools", toolIds.size());
            
            CompletableFuture<Map<String, Map<Long, List<Map<String, Object>>>>> asyncDataFuture = 
                asyncDataService.loadAllToolDataAsync(toolIds);
            
            // Wait for all async operations to complete
            Map<String, Map<Long, List<Map<String, Object>>>> asyncData = asyncDataFuture.get();
            
            // Extract the data maps
            toolRmasMap = asyncData.getOrDefault("rmas", new HashMap<>());
            toolPassdownsMap = asyncData.getOrDefault("passdowns", new HashMap<>());
            toolCommentsMap = asyncData.getOrDefault("comments", new HashMap<>());
            toolTrackTrendsMap = asyncData.getOrDefault("trackTrends", new HashMap<>());
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("=== COMPLETED ASYNC TOOLS LIST PAGE LOAD IN {}ms ===", duration);
            
            // Add performance info to model for display
            model.addAttribute("loadTime", duration);
            model.addAttribute("asyncEnabled", true);
            
        } catch (Exception e) {
            logger.error("Error in async tools list loading, falling back to empty maps: {}", e.getMessage(), e);
            
            // Maps are already initialized above, so we just keep them empty
            model.addAttribute("asyncEnabled", false);
            model.addAttribute("asyncError", e.getMessage());
        }
        
        // Ensure all tools have entries in maps (even if empty)
        for (Long toolId : toolIds) {
            toolRmasMap.computeIfAbsent(toolId, k -> new ArrayList<>());
            toolPassdownsMap.computeIfAbsent(toolId, k -> new ArrayList<>());
            toolCommentsMap.computeIfAbsent(toolId, k -> new ArrayList<>());
            toolTrackTrendsMap.computeIfAbsent(toolId, k -> new ArrayList<>());
        }
        
        // Sort: tools with technicians first, then alphabetically by name and secondary name
        allTools.sort((a, b) -> {
            boolean aHasTech = a.getCurrentTechnicians() != null && !a.getCurrentTechnicians().isEmpty();
            boolean bHasTech = b.getCurrentTechnicians() != null && !b.getCurrentTechnicians().isEmpty();
            if (aHasTech && !bHasTech) return -1;
            if (!aHasTech && bHasTech) return 1;
            int cmp = a.getName().compareToIgnoreCase(b.getName());
            if (cmp != 0) return cmp;
            String aSec = a.getSecondaryName() == null ? "" : a.getSecondaryName();
            String bSec = b.getSecondaryName() == null ? "" : b.getSecondaryName();
            return aSec.compareToIgnoreCase(bSec);
        });
        
        model.addAttribute("tools", allTools);
        model.addAttribute("toolRmasMap", toolRmasMap);
        model.addAttribute("toolPassdownsMap", toolPassdownsMap);
        model.addAttribute("toolCommentsMap", toolCommentsMap);
        model.addAttribute("toolTrackTrendsMap", toolTrackTrendsMap);
        
        logger.info("=== COMPLETED TOOLS LIST PAGE LOADING (ULTRA-OPTIMIZED) ===");
        logger.info("Final counts - Tools: {}, RMAs: {}, Passdowns: {}, Comments: {}, Track/Trends: {}", 
                   allTools.size(),
                   toolRmasMap.values().stream().mapToInt(List::size).sum(),
                   toolPassdownsMap.values().stream().mapToInt(List::size).sum(),
                   toolCommentsMap.values().stream().mapToInt(List::size).sum(),
                   toolTrackTrendsMap.values().stream().mapToInt(List::size).sum());
        
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
        logger.info("=== LOADING TOOL DETAILS FOR ID: {} ===", id);
        
        // Add all tools for the move document/picture dropdowns and moving parts chain display
        List<Tool> allTools = toolService.getAllTools();
        model.addAttribute("allTools", allTools);
        
        Optional<Tool> toolOpt = toolService.getToolById(id);
        if (toolOpt.isPresent()) {
            Tool tool = toolOpt.get();
            logger.info("Found tool: {}", tool.getName());
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
            
            // Fetch Comments associated with this tool (using new comment system)
            List<ToolComment> toolComments = toolService.getCommentsForTool(id);
            model.addAttribute("toolComments", toolComments);
            logger.info("Found {} Comments associated with tool ID: {}", toolComments.size(), id);
            
            // Get current user and check if assigned to this tool
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                String email = auth.getName();
                userService.getUserByEmail(email).ifPresent(currentUser -> {
                    model.addAttribute("currentUser", currentUser);
                    boolean isCurrentUserAssigned = usersWithActiveTool.stream()
                            .anyMatch(user -> user.getId().equals(currentUser.getId()));
                    model.addAttribute("isCurrentUserAssigned", isCurrentUserAssigned);
                    
                    // Determine likely last updater for checklist
                    User lastUpdatedBy = null;
                    if (isCurrentUserAssigned) {
                        // If current user is assigned, they're likely the last updater
                        lastUpdatedBy = currentUser;
                    } else if (!usersWithActiveTool.isEmpty()) {
                        // Otherwise, use the first assigned technician
                        lastUpdatedBy = usersWithActiveTool.get(0);
                    }
                    model.addAttribute("checklistLastUpdatedBy", lastUpdatedBy);
                });
            } else {
                // No authenticated user, but still try to show an assigned technician if available
                if (!usersWithActiveTool.isEmpty()) {
                    model.addAttribute("checklistLastUpdatedBy", usersWithActiveTool.get(0));
                }
            }
            
            // Fetch Moving Parts associated with this tool (both as source and destination)
            logger.info("About to fetch moving parts for tool ID: {}", id);
            List<MovingPart> movingParts = movingPartService.getMovingPartsByToolId(id);
            logger.info("MovingPartService returned {} moving parts for tool ID: {}", movingParts.size(), id);
            model.addAttribute("movingParts", movingParts);
            model.addAttribute("movingPartService", movingPartService);
            logger.info("Found {} Moving Parts associated with tool ID: {}", movingParts.size(), id);
            
            // Create destination chain display data for each moving part (similar to RMA controller)
            Map<Long, List<Tool>> movingPartDestinationChains = new HashMap<>();
            Map<Long, Tool> toolMap = new HashMap<>();
            for (Tool t : allTools) {
                toolMap.put(t.getId(), t);
            }
            
            for (MovingPart movingPart : movingParts) {
                List<Tool> chainTools = new ArrayList<>();
                List<Long> destinationIds = movingPart.getDestinationToolIds();
                for (Long toolId : destinationIds) {
                    Tool destTool = toolMap.get(toolId);
                    if (destTool != null) {
                        chainTools.add(destTool);
                    }
                }
                movingPartDestinationChains.put(movingPart.getId(), chainTools);
            }
            model.addAttribute("movingPartDestinationChains", movingPartDestinationChains);
            
            // Create a new Note object for the note creation form
            model.addAttribute("newNote", new Note());
            
            // Fetch Track/Trends associated with this tool
            List<TrackTrend> trackTrendsForTool = trackTrendService.getTrackTrendsByToolId(id);
            model.addAttribute("trackTrendsForTool", trackTrendsForTool);
        } else {
            logger.warn("Tool with ID {} not found", id);
        }
        
        // Add lightweight RMA data for link functionality (only id and rmaNumber needed)
        List<Rma> allRmas = rmaRepository.findAllForListView();
        model.addAttribute("allRmas", allRmas);
        
        // Add recent passdowns for link functionality
        List<Passdown> recentPassdowns = passdownService.getRecentPassdowns(20);
        model.addAttribute("recentPassdowns", recentPassdowns);
        
        // Add lightweight TrackTrends for linking functionality (only id and name needed)
        List<TrackTrend> allTrackTrends = trackTrendService.getAllTrackTrends();
        model.addAttribute("allTrackTrends", allTrackTrends);
        
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
            @RequestParam(value = "files", required = false) MultipartFile[] files) {
        
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
            
            // Handle document uploads (now part of 'files')
            logger.info("Processing file uploads from 'files' parameter");
            if (files != null && files.length > 0) {
                logger.info("Found {} files to process", files.length);
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        String originalFilename = file.getOriginalFilename();
                        String contentType = file.getContentType();
                        String subdirectory;
                        boolean isPicture = false;

                        if (contentType != null && contentType.startsWith("image/")) {
                            subdirectory = "pictures";
                            isPicture = true;
                        } else {
                            subdirectory = "documents";
                        }
                        logger.info("Processing file: {}, type: {}, target subdir: {}", originalFilename, contentType, subdirectory);

                        String filePath = saveUploadedFile(file, subdirectory);
                        if (filePath != null) {
                            if (isPicture) {
                                tool.getPicturePaths().add(filePath);
                                tool.getPictureNames().put(filePath, originalFilename);
                                logger.info("Added picture path: {} for file {}", filePath, originalFilename);
                            } else {
                                tool.getDocumentPaths().add(filePath);
                                tool.getDocumentNames().put(filePath, originalFilename);
                                logger.info("Added document path: {} for file {}", filePath, originalFilename);
                            }
                        } else {
                            logger.warn("Failed to save uploaded file: {}", originalFilename);
                        }
                    }
                }
            } else {
                logger.info("No files provided in 'files' parameter for upload");
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
    @Transactional
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
    @Transactional
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

    // TEMPORARILY DISABLED - Note methods require NoteService
    /*
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
    */

    @PostMapping("/{id}/post-comment")
    public String postComment(@PathVariable Long id, 
                            @RequestParam("content") String content,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            String userEmail = authentication.getName();
            toolService.addComment(id, content, userEmail);
            redirectAttributes.addFlashAttribute("message", "Comment added successfully");
        } catch (Exception e) {
            logger.error("Error adding comment to Tool {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error adding comment: " + e.getMessage());
        }
        return "redirect:/tools/" + id;
    }

    @PostMapping("/{toolId}/comments/{commentId}/update")
    public String editComment(@PathVariable Long toolId, 
                             @PathVariable Long commentId,
                             @RequestParam("content") String content,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            String userEmail = authentication.getName();
            toolService.editComment(commentId, content, userEmail);
            redirectAttributes.addFlashAttribute("message", "Comment updated successfully");
        } catch (Exception e) {
            logger.error("Error editing comment from Tool {}: {}", toolId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error editing comment: " + e.getMessage());
        }
        return "redirect:/tools/" + toolId;
    }

    @PostMapping("/{toolId}/comments/{commentId}/remove")
    public String deleteComment(@PathVariable Long toolId, 
                              @PathVariable Long commentId,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            String userEmail = authentication.getName();
            toolService.deleteComment(commentId, userEmail);
            redirectAttributes.addFlashAttribute("message", "Comment deleted successfully");
        } catch (Exception e) {
            logger.error("Error deleting comment from Tool {}: {}", toolId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error deleting comment: " + e.getMessage());
        }
        return "redirect:/tools/" + toolId;
    }

    @PostMapping("/{id}/upload-files")
    public String handleFileUpload(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files,
            RedirectAttributes redirectAttributes) {
        
        logger.info("Attempting to upload {} files to tool ID: {}", files.length, id);
        Optional<Tool> toolOpt = toolService.getToolById(id);
        if (toolOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Tool not found with ID: " + id);
            return "redirect:/tools";
        }
        Tool tool = toolOpt.get();
        int uploadedCount = 0;
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            String subdirectory;
            boolean isPicture = false;

            if (contentType != null && contentType.startsWith("image/")) {
                subdirectory = "pictures";
                isPicture = true;
            } else {
                // Default to documents for other types, or could add more specific checks
                subdirectory = "documents";
            }
            
            logger.info("Processing file: {}, type: {}, target subdir: {}", originalFilename, contentType, subdirectory);

            // The saveUploadedFile method already catches IOException and returns null on failure.
            String filePath = saveUploadedFile(file, subdirectory); 
            if (filePath != null) {
                if (isPicture) {
                    tool.getPicturePaths().add(filePath);
                    tool.getPictureNames().put(filePath, originalFilename);
                    logger.info("Added picture: {} to tool {}", filePath, tool.getId());
                } else {
                    tool.getDocumentPaths().add(filePath);
                    tool.getDocumentNames().put(filePath, originalFilename);
                    logger.info("Added document: {} to tool {}", filePath, tool.getId());
                }
                uploadedCount++;
            } else {
                logger.warn("Failed to save file: {}", originalFilename);
                errors.add("Failed to save file: " + originalFilename);
            }
        }

        if (uploadedCount > 0) {
            try {
                toolService.saveTool(tool);
                redirectAttributes.addFlashAttribute("message", uploadedCount + " file(s) uploaded successfully.");
            } catch (Exception e) {
                logger.error("Error saving tool after file uploads: {}", e.getMessage(), e);
                errors.add("Error saving tool after uploads: " + e.getMessage());
                redirectAttributes.addFlashAttribute("error", String.join(", ", errors));
            }
        } else if (!errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "File upload failed: " + String.join(", ", errors));
        } else {
            redirectAttributes.addFlashAttribute("info", "No files were selected for upload.");
        }

        return "redirect:/tools/" + id;
    }

    /**
     * Upload documents to a tool
     */
    @PostMapping("/{id}/upload-documents")
    public String uploadDocuments(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files,
            RedirectAttributes redirectAttributes) {
        
        logger.info("Uploading {} documents to tool ID: {}", files.length, id);
        return handleSpecificFileUpload(id, files, false, redirectAttributes);
    }

    /**
     * Upload pictures to a tool
     */
    @PostMapping("/{id}/upload-pictures")
    public String uploadPictures(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files,
            RedirectAttributes redirectAttributes) {
        
        logger.info("Uploading {} pictures to tool ID: {}", files.length, id);
        return handleSpecificFileUpload(id, files, true, redirectAttributes);
    }

    /**
     * Common method to handle file uploads for tools
     */
    private String handleSpecificFileUpload(
            Long id,
            MultipartFile[] files,
            boolean forceAsPictures,
            RedirectAttributes redirectAttributes) {
        
        Optional<Tool> toolOpt = toolService.getToolById(id);
        if (toolOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Tool not found with ID: " + id);
            return "redirect:/tools";
        }
        
        Tool tool = toolOpt.get();
        int uploadedCount = 0;
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            String subdirectory;
            boolean isPicture;

            if (forceAsPictures) {
                subdirectory = "pictures";
                isPicture = true;
            } else {
                if (contentType != null && contentType.startsWith("image/")) {
                    subdirectory = "pictures";
                    isPicture = true;
                } else {
                    subdirectory = "documents";
                    isPicture = false;
                }
            }
            
            logger.info("Processing file: {}, type: {}, target subdir: {}", originalFilename, contentType, subdirectory);

            String filePath = saveUploadedFile(file, subdirectory); 
            if (filePath != null) {
                if (isPicture) {
                    tool.getPicturePaths().add(filePath);
                    tool.getPictureNames().put(filePath, originalFilename);
                    logger.info("Added picture: {} to tool {}", filePath, tool.getId());
                } else {
                    tool.getDocumentPaths().add(filePath);
                    tool.getDocumentNames().put(filePath, originalFilename);
                    logger.info("Added document: {} to tool {}", filePath, tool.getId());
                }
                uploadedCount++;
            } else {
                logger.warn("Failed to save file: {}", originalFilename);
                errors.add("Failed to save file: " + originalFilename);
            }
        }

        if (uploadedCount > 0) {
            try {
                toolService.saveTool(tool);
                String fileType = forceAsPictures ? "picture(s)" : "file(s)";
                redirectAttributes.addFlashAttribute("message", uploadedCount + " " + fileType + " uploaded successfully.");
            } catch (Exception e) {
                logger.error("Error saving tool after file uploads: {}", e.getMessage(), e);
                errors.add("Error saving tool after uploads: " + e.getMessage());
                redirectAttributes.addFlashAttribute("error", String.join(", ", errors));
            }
        } else if (!errors.isEmpty()) {
            String fileType = forceAsPictures ? "picture" : "file";
            redirectAttributes.addFlashAttribute("error", fileType + " upload failed: " + String.join(", ", errors));
        } else {
            String fileType = forceAsPictures ? "pictures" : "files";
            redirectAttributes.addFlashAttribute("info", "No " + fileType + " were selected for upload.");
        }

        return "redirect:/tools/" + id;
    }
    
    /**
     * Update tool checklist dates
     */
    @PostMapping("/{id}/update-checklist")
    public String updateChecklist(@PathVariable Long id,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate commissionDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate preSl1Date,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sl1Date,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mechanicalPreSl1Date,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mechanicalPostSl1Date,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate specificInputFunctionalityDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate modesOfOperationDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate specificSoosDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fieldServiceReportDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate certificateOfApprovalDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate turnedOverToCustomerDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startUpSl03Date,
                                 RedirectAttributes redirectAttributes) {
        
        try {
            logger.info("Updating checklist for tool ID: {}", id);
            
            Optional<Tool> toolOpt = toolService.getToolById(id);
            if (toolOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Tool not found");
                return "redirect:/tools";
            }
            
            Tool tool = toolOpt.get();
            
            // Update dates (completion status is automatically determined by date presence)
            tool.setCommissionDate(commissionDate);
            tool.setPreSl1Date(preSl1Date);
            tool.setSl1Date(sl1Date);
            tool.setMechanicalPreSl1Date(mechanicalPreSl1Date);
            tool.setMechanicalPostSl1Date(mechanicalPostSl1Date);
            tool.setSpecificInputFunctionalityDate(specificInputFunctionalityDate);
            tool.setModesOfOperationDate(modesOfOperationDate);
            tool.setSpecificSoosDate(specificSoosDate);
            tool.setFieldServiceReportDate(fieldServiceReportDate);
            tool.setCertificateOfApprovalDate(certificateOfApprovalDate);
            tool.setTurnedOverToCustomerDate(turnedOverToCustomerDate);
            tool.setStartUpSl03Date(startUpSl03Date);
            
            // Save the updated tool
            toolService.saveTool(tool);
            
            redirectAttributes.addFlashAttribute("success", "Tool checklist updated successfully");
            logger.info("Successfully updated checklist for tool ID: {}", id);
            
        } catch (Exception e) {
            logger.error("Error updating tool checklist", e);
            redirectAttributes.addFlashAttribute("error", "Error updating checklist: " + e.getMessage());
        }
        
        return "redirect:/tools/" + id;
    }
    
    // Moving parts functionality is handled by MovingPartController

    @PostMapping("/upload-excel")
    @ResponseBody
    public Map<String, Object> uploadToolsFromExcel(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("=== TOOL EXCEL UPLOAD STARTED ===");
            logger.info("Received file: {}, size: {} bytes, content type: {}", 
                file.getOriginalFilename(), file.getSize(), file.getContentType());
            
            if (file.isEmpty()) {
                logger.warn("Uploaded file is empty");
                response.put("success", false);
                response.put("error", "Please select a file to upload");
                return response;
            }

            // Validate file type
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || 
                (!originalFilename.toLowerCase().endsWith(".xlsx") && 
                 !originalFilename.toLowerCase().endsWith(".xls"))) {
                response.put("success", false);
                response.put("error", "Please upload an Excel file (.xlsx or .xls)");
                return response;
            }

            // Parse Excel file and create tools
            int toolsCreated = toolService.createToolsFromExcel(file);
            
            if (toolsCreated > 0) {
                response.put("success", true);
                response.put("toolsCreated", toolsCreated);
                logger.info("Successfully created {} tools from Excel upload", toolsCreated);
            } else {
                response.put("success", false);
                response.put("error", "No valid tools were found in the Excel file. Please check the format and data.");
            }
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid Excel format: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing Excel file: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Error processing Excel file: " + e.getMessage());
        }
        
        return response;
    }

    @GetMapping("/reference-documents/{filename}")
    public ResponseEntity<Resource> downloadReferenceDocument(@PathVariable String filename) {
        try {
            // Security: Only allow specific safe filenames
            if (!filename.matches("^[a-zA-Z0-9._-]+\\.(xlsx|xls|pdf|docx|doc)$")) {
                return ResponseEntity.badRequest().build();
            }
            
            // Load resource from classpath
            String resourcePath = "/reference-documents/" + filename;
            InputStream resourceStream = getClass().getResourceAsStream(resourcePath);
            
            if (resourceStream == null) {
                logger.warn("Reference document not found in classpath: {}", filename);
                return ResponseEntity.notFound().build();
            }
            
            // Create resource from input stream
            Resource resource = new InputStreamResource(resourceStream);
            
            // Determine content type
            String contentType = "application/octet-stream";
            if (filename.toLowerCase().endsWith(".xlsx")) {
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else if (filename.toLowerCase().endsWith(".xls")) {
                contentType = "application/vnd.ms-excel";
            } else if (filename.toLowerCase().endsWith(".pdf")) {
                contentType = "application/pdf";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + filename + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            logger.error("Error serving reference document: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 