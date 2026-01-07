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
import com.pcd.manager.model.NCSR;
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
import com.pcd.manager.service.ChecklistTemplateService;
import com.pcd.manager.service.NCSRService;
import com.pcd.manager.service.CustomLocationService;
import com.pcd.manager.service.ToolTypeFieldDefinitionService;
import com.pcd.manager.model.ToolTypeFieldDefinition;
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
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.security.Principal;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final ChecklistTemplateService checklistTemplateService;
    private final NCSRService ncsrService;
    private final CustomLocationService customLocationService;
    private final ToolTypeFieldDefinitionService fieldDefinitionService;
    
    @Value("${app.upload.dir:${user.home}/uploads}")
    private String uploadDir;

    @Autowired
    public ToolController(ToolService toolService, ToolRepository toolRepository, LocationService locationService, RmaService rmaService, UserService userService, TrackTrendService trackTrendService, PassdownService passdownService, RmaRepository rmaRepository, PassdownRepository passdownRepository, ToolCommentRepository toolCommentRepository, TrackTrendRepository trackTrendRepository, AsyncDataService asyncDataService, MovingPartService movingPartService, ChecklistTemplateService checklistTemplateService, NCSRService ncsrService, CustomLocationService customLocationService, ToolTypeFieldDefinitionService fieldDefinitionService) {
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
        this.checklistTemplateService = checklistTemplateService;
        this.ncsrService = ncsrService;
        this.customLocationService = customLocationService;
        this.fieldDefinitionService = fieldDefinitionService;
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
    public String listTools(@RequestParam(value = "typeView", required = false) String typeView, Model model) {
        logger.info("=== LOADING TOOLS LIST PAGE (ULTRA-OPTIMIZED) ===");
        long startTime = System.currentTimeMillis();
        
        // Get user's active location for filtering
        String filterLocationName = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            Optional<User> userOpt = userService.getUserByEmail(auth.getName());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.getActiveSite() != null) {
                    filterLocationName = user.getActiveSite().getDisplayName() != null ? 
                                       user.getActiveSite().getDisplayName() : user.getActiveSite().getName();
                    logger.info("Filtering tools by user's active location: {}", filterLocationName);
                }
            }
        }
        
        // Use ultra-lightweight query for list view - only load essential fields as Object[]
        List<Object[]> toolData = toolRepository.findAllForAsyncListView();
        logger.info("Loaded {} tools for ultra-optimized list view", toolData.size());
        
        // Filter by location if user has an active location
        if (filterLocationName != null && !toolData.isEmpty()) {
            List<Object[]> filteredToolData = new ArrayList<>();
            for (Object[] row : toolData) {
                // locationName is at index 9
                String locationName = (String) row[9];
                if (locationName != null && locationName.equals(filterLocationName)) {
                    filteredToolData.add(row);
                }
            }
            toolData = filteredToolData;
            logger.info("Filtered to {} tools for location: {}", toolData.size(), filterLocationName);
        }
        
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
        java.util.Set<Long> gasguardRawIds = new java.util.HashSet<>();
        
        for (Object[] row : toolData) {
            Tool tool = new Tool();
            tool.setId((Long) row[0]);
            tool.setName((String) row[1]);
            tool.setSecondaryName((String) row[2]);
            // Convert String from native query to ToolType enum
            String toolTypeStr = (String) row[3];
            if (toolTypeStr != null) {
                try {
                    // Map legacy GASGUARD to AMATGASGUARD for list purposes
                    if ("GASGUARD".equalsIgnoreCase(toolTypeStr)) {
                        tool.setToolType(Tool.ToolType.AMATGASGUARD);
                    } else {
                        tool.setToolType(Tool.ToolType.valueOf(toolTypeStr));
                    }
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown tool type '{}' for tool {}, setting to null", toolTypeStr, tool.getName());
                    tool.setToolType(null);
                }
                // Track gasguard ids based on raw DB string too
                if ("GASGUARD".equalsIgnoreCase(toolTypeStr) || "AMATGASGUARD".equalsIgnoreCase(toolTypeStr)) {
                    gasguardRawIds.add((Long) row[0]);
                }
            }
            // Filter by typeView if provided, but NEVER exclude GasGuard from the page
            if (typeView != null && !typeView.isBlank() && !"ALL".equalsIgnoreCase(typeView)) {
                if (tool.getToolType() != Tool.ToolType.AMATGASGUARD) {
                    if (tool.getToolType() == null || !tool.getToolType().name().equalsIgnoreCase(typeView)) {
                        continue;
                    }
                }
            }
            tool.setSerialNumber1((String) row[4]);
            tool.setSerialNumber2((String) row[5]);
            tool.setModel1((String) row[6]);
            tool.setModel2((String) row[7]);
            // Skip the stored status - we'll calculate it dynamically
            // tool.setStatus((Tool.ToolStatus) row[8]);
            
            // Set location name directly from query
            String locationName = (String) row[9];
            tool.setLocationName(locationName != null ? locationName : "Unknown Location");
            
            // Set timestamp fields (convert from Timestamp to LocalDateTime)
            java.sql.Timestamp createdTimestamp = (java.sql.Timestamp) row[10];
            java.sql.Timestamp updatedTimestamp = (java.sql.Timestamp) row[11];
            tool.setCreatedAt(createdTimestamp != null ? createdTimestamp.toLocalDateTime() : null);
            tool.setUpdatedAt(updatedTimestamp != null ? updatedTimestamp.toLocalDateTime() : null);
            
            // Set checklist date fields for status popover (convert from java.sql.Date to LocalDate)
            tool.setCommissionDate(convertSqlDateToLocalDate((java.sql.Date) row[12]));
            tool.setPreSl1Date(convertSqlDateToLocalDate((java.sql.Date) row[13]));
            tool.setSl1Date(convertSqlDateToLocalDate((java.sql.Date) row[14]));
            tool.setMechanicalPreSl1Date(convertSqlDateToLocalDate((java.sql.Date) row[15]));
            tool.setMechanicalPostSl1Date(convertSqlDateToLocalDate((java.sql.Date) row[16]));
            tool.setSpecificInputFunctionalityDate(convertSqlDateToLocalDate((java.sql.Date) row[17]));
            tool.setModesOfOperationDate(convertSqlDateToLocalDate((java.sql.Date) row[18]));
            tool.setSpecificSoosDate(convertSqlDateToLocalDate((java.sql.Date) row[19]));
            tool.setFieldServiceReportDate(convertSqlDateToLocalDate((java.sql.Date) row[20]));
            tool.setCertificateOfApprovalDate(convertSqlDateToLocalDate((java.sql.Date) row[21]));
            tool.setTurnedOverToCustomerDate(convertSqlDateToLocalDate((java.sql.Date) row[22]));
            tool.setStartUpSl03Date(convertSqlDateToLocalDate((java.sql.Date) row[23]));
            
            // Set checklist labels JSON for locked checklist behavior
            tool.setChecklistLabelsJson((String) row[24]);
            
            // Set upload date (index 25)
            tool.setUploadDate(convertSqlTimestampToLocalDateTime((java.sql.Timestamp) row[25]));
            
            // Set checklist completion boolean flags (indices 26-37)
            tool.setCommissionComplete((Boolean) row[26]);
            tool.setPreSl1Complete((Boolean) row[27]);
            tool.setSl1Complete((Boolean) row[28]);
            tool.setMechanicalPreSl1Complete((Boolean) row[29]);
            tool.setMechanicalPostSl1Complete((Boolean) row[30]);
            tool.setSpecificInputFunctionalityComplete((Boolean) row[31]);
            tool.setModesOfOperationComplete((Boolean) row[32]);
            tool.setSpecificSoosComplete((Boolean) row[33]);
            tool.setFieldServiceReportComplete((Boolean) row[34]);
            tool.setCertificateOfApprovalComplete((Boolean) row[35]);
            tool.setTurnedOverToCustomerComplete((Boolean) row[36]);
            tool.setStartUpSl03Complete((Boolean) row[37]);
            
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
        
        // Load document and picture counts
        Map<Long, Integer> documentCountsMap = new HashMap<>();
        Map<Long, Integer> pictureCountsMap = new HashMap<>();
        
        if (!toolIds.isEmpty()) {
            // Load document counts
            List<Object[]> documentCounts = toolRepository.findDocumentCountsByToolIds(toolIds);
            for (Object[] row : documentCounts) {
                Long toolId = (Long) row[0];
                Integer count = ((Number) row[1]).intValue();
                documentCountsMap.put(toolId, count);
            }
            
            // Load picture counts
            List<Object[]> pictureCounts = toolRepository.findPictureCountsByToolIds(toolIds);
            for (Object[] row : pictureCounts) {
                Long toolId = (Long) row[0];
                Integer count = ((Number) row[1]).intValue();
                pictureCountsMap.put(toolId, count);
            }
            
            // Set the document and picture data on Tool objects for Thymeleaf access
            for (Tool tool : allTools) {
                // Initialize collections so Thymeleaf can count them
                int docCount = documentCountsMap.getOrDefault(tool.getId(), 0);
                int picCount = pictureCountsMap.getOrDefault(tool.getId(), 0);
                
                // Create dummy collections with the right size for Thymeleaf counting
                tool.setDocumentPaths(new HashSet<>(Collections.nCopies(docCount, "dummy")));
                tool.setPicturePaths(new HashSet<>(Collections.nCopies(picCount, "dummy")));
                
                logger.debug("Tool {}: {} documents, {} pictures", tool.getName(), docCount, picCount);
            }
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
        
        // Split into Standard vs GasGuard lists for separate rendering tables
        List<Tool> standardTools = new ArrayList<>();
        List<Long> gasguardIds = new ArrayList<>();
        for (Tool t : allTools) {
            if (t.getToolType() == Tool.ToolType.AMATGASGUARD || gasguardRawIds.contains(t.getId())) {
                gasguardIds.add(t.getId());
            } else {
                standardTools.add(t);
            }
        }

        // Load full entities for GasGuard tools so specific fields are present
        List<Tool> gasguardTools = new ArrayList<>();
        if (!gasguardIds.isEmpty()) {
            gasguardTools = toolRepository.findAllById(gasguardIds);
            // Attach technicians and counts like we did for the lightweight list
            for (Tool g : gasguardTools) {
                List<User> technicians = toolTechniciansMap.getOrDefault(g.getId(), new ArrayList<>());
                g.setCurrentTechnicians(new HashSet<>(technicians));
                // Create dummy collections with the right size for Thymeleaf counting
                int docCount = documentCountsMap.getOrDefault(g.getId(), 0);
                int picCount = pictureCountsMap.getOrDefault(g.getId(), 0);
                g.setDocumentPaths(new HashSet<>(Collections.nCopies(docCount, "dummy")));
                g.setPicturePaths(new HashSet<>(Collections.nCopies(picCount, "dummy")));
                // Calculate status from checklist dates
                g.setStatus(g.getCalculatedStatus());
            }
        }

        // Sort: tools with technicians first, then alphabetically by name and secondary name
        standardTools.sort((a, b) -> {
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

        // GasGuard sort default: uploadDate ASC (oldest first); fallback to updatedAt, then createdAt, then system/location
        gasguardTools.sort((a, b) -> {
            LocalDateTime aUpload = a.getUploadDate();
            LocalDateTime bUpload = b.getUploadDate();
            if (aUpload != null || bUpload != null) {
                if (aUpload == null) return -1; // nulls first
                if (bUpload == null) return 1;
                int cmpUpload = aUpload.compareTo(bUpload);
                if (cmpUpload != 0) return cmpUpload;
            }
            LocalDateTime aUpd = a.getUpdatedAt();
            LocalDateTime bUpd = b.getUpdatedAt();
            if (aUpd != null || bUpd != null) {
                if (aUpd == null) return 1; // put with null updatedAt later
                if (bUpd == null) return -1;
                int cmpUpd = bUpd.compareTo(aUpd); // DESC
                if (cmpUpd != 0) return cmpUpd;
            }
            LocalDateTime aCre = a.getCreatedAt();
            LocalDateTime bCre = b.getCreatedAt();
            if (aCre != null || bCre != null) {
                if (aCre == null) return 1;
                if (bCre == null) return -1;
                int cmpCre = bCre.compareTo(aCre); // DESC
                if (cmpCre != 0) return cmpCre;
            }
            String aSys = a.getSystemName() == null ? "" : a.getSystemName();
            String bSys = b.getSystemName() == null ? "" : b.getSystemName();
            int cmp = aSys.compareToIgnoreCase(bSys);
            if (cmp != 0) return cmp;
            String aLoc = a.getEquipmentLocation() == null ? "" : a.getEquipmentLocation();
            String bLoc = b.getEquipmentLocation() == null ? "" : b.getEquipmentLocation();
            return aLoc.compareToIgnoreCase(bLoc);
        });

        // Create checklist data for status popovers - need full tool entities for this
        Map<Long, List<Map<String, Object>>> toolChecklistMap = new HashMap<>();
        
        // Get checklist data for all tools (both standard and gasguard)
        List<Tool> allFullTools = new ArrayList<>();
        allFullTools.addAll(standardTools);
        allFullTools.addAll(gasguardTools);
        
        for (Tool tool : allFullTools) {
            List<ChecklistTemplateService.ChecklistItem> checklistItems = checklistTemplateService.getChecklistForTool(tool);
            List<Map<String, Object>> itemMaps = convertChecklistItemsToMaps(checklistItems);
            toolChecklistMap.put(tool.getId(), itemMaps);
        }
        
        // Provide both lists to the template
        model.addAttribute("tools", standardTools); // keep legacy name to avoid breaking other bindings
        model.addAttribute("standardTools", standardTools);
        model.addAttribute("gasguardTools", gasguardTools);
        model.addAttribute("toolChecklistMap", toolChecklistMap);
        model.addAttribute("typeView", typeView == null ? "ALL" : typeView);
        model.addAttribute("toolRmasMap", toolRmasMap);
        model.addAttribute("toolPassdownsMap", toolPassdownsMap);
        model.addAttribute("toolCommentsMap", toolCommentsMap);
        model.addAttribute("toolTrackTrendsMap", toolTrackTrendsMap);
        // toolChecklistMap removed since Status columns are gone
        
        // Add all locations for the location filter
        model.addAttribute("locations", locationService.getAllLocations());
        
        // Add current user for default location filter (reuse auth variable from above)
        if (auth != null && auth.getName() != null) {
            userService.getUserByEmail(auth.getName()).ifPresent(currentUser -> {
                model.addAttribute("currentUser", currentUser);
                // Add custom locations for the user's active location
                if (currentUser.getActiveSite() != null) {
                    java.util.Map<com.pcd.manager.model.CustomLocation, Integer> customLocationsWithCounts = 
                        customLocationService.getCustomLocationsWithPartCounts(currentUser.getActiveSite());
                    model.addAttribute("customLocations", customLocationsWithCounts);
                }
            });
        }
        
        logger.info("=== COMPLETED TOOLS LIST PAGE LOADING (ULTRA-OPTIMIZED) ===");
        logger.info("Final counts - Tools: {}, RMAs: {}, Passdowns: {}, Comments: {}, Track/Trends: {}", 
                   allTools.size(),
                   toolRmasMap.values().stream().mapToInt(List::size).sum(),
                   toolPassdownsMap.values().stream().mapToInt(List::size).sum(),
                   toolCommentsMap.values().stream().mapToInt(List::size).sum(),
                   toolTrackTrendsMap.values().stream().mapToInt(List::size).sum());
        
        return "tools/list";
    }

    /**
     * Convert ChecklistTemplateService.ChecklistItem objects to Maps for Thymeleaf template processing
     */
    private List<Map<String, Object>> convertChecklistItemsToMaps(List<ChecklistTemplateService.ChecklistItem> checklistItems) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChecklistTemplateService.ChecklistItem item : checklistItems) {
            Map<String, Object> map = new HashMap<>();
            map.put("label", item.label);
            map.put("date", item.date);
            map.put("completed", item.completed);
            result.add(map);
        }
        return result;
    }

    @GetMapping("/new")
    public String showCreateForm(@RequestParam(value = "type", required = false) String type,
                                 Model model, Authentication authentication) {
        Tool tool = new Tool();
        // Preselect tool type if provided
        if (type != null && !type.isBlank()) {
            try {
                tool.setToolType(Tool.ToolType.valueOf(type));
            } catch (IllegalArgumentException ignore) {}
        }
        
        // Set location to user's active site if available, otherwise fall back to system default
        if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            String userEmail = authentication.getName();
            Optional<User> userOpt = userService.getUserByEmail(userEmail);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.getActiveSite() != null) {
                    tool.setLocationName(user.getActiveSite().getDisplayName() != null ? 
                                       user.getActiveSite().getDisplayName() : user.getActiveSite().getName());
                    logger.info("Set tool location to user's active site: {}", user.getActiveSite().getDisplayName());
                } else {
                    // Fall back to system default if user has no active site
                    locationService.getDefaultLocation().ifPresent(defaultLocation -> {
                        tool.setLocationName(defaultLocation.getDisplayName() != null ? 
                                           defaultLocation.getDisplayName() : defaultLocation.getName());
                        logger.info("User has no active site, using system default location: {}", defaultLocation.getDisplayName());
                    });
                }
            }
        } else {
            // User not authenticated, use system default
            locationService.getDefaultLocation().ifPresent(defaultLocation -> {
                tool.setLocationName(defaultLocation.getDisplayName() != null ? 
                                   defaultLocation.getDisplayName() : defaultLocation.getName());
            });
        }
        
        model.addAttribute("tool", tool);
        model.addAttribute("locations", locationService.getAllLocations());
        
        // Load field definitions and custom field values for the tool type
        if (tool.getToolType() != null) {
            String toolType = tool.getToolType().name();
            List<ToolTypeFieldDefinition> fieldDefinitions = fieldDefinitionService.getFieldDefinitionsForToolType(toolType);
            model.addAttribute("fieldDefinitions", fieldDefinitions);
            
            // Load existing custom field values if editing
            if (tool.getId() != null) {
                Map<String, String> customFields = toolService.getCustomFieldsForTool(tool.getId());
                model.addAttribute("customFields", customFields);
            }
        }
        
        if (tool.getToolType() == Tool.ToolType.AMATGASGUARD) {
            // Provide dynamic checklist labels based on GasGuard template
            model.addAttribute("checklistItems", checklistTemplateService.getChecklistForTool(tool));
            return "tools/form-gasguard";
        }
        return "tools/form";
    }

    @GetMapping("/{id}")
    public String showToolDetails(@PathVariable Long id, Model model, Principal principal) {
        logger.info("=== LOADING TOOL DETAILS FOR ID: {} ===", id);
        
        // Add all tools for the move document/picture dropdowns and moving parts chain display
        List<Tool> allTools = toolService.getAllTools();
        // Sort tools alphabetically by name
        allTools.sort((t1, t2) -> {
            String name1 = t1.getName() != null ? t1.getName().toLowerCase() : "";
            String name2 = t2.getName() != null ? t2.getName().toLowerCase() : "";
            return name1.compareTo(name2);
        });
        model.addAttribute("allTools", allTools);
        
        Optional<Tool> toolOpt = toolService.getToolById(id);
        if (toolOpt.isPresent()) {
            Tool tool = toolOpt.get();
            logger.info("Found tool: {}", tool.getName());
            model.addAttribute("tool", tool);
            // Add checklist items resolved from template (labels) mapped to this tool's date fields
            model.addAttribute("checklistItems", checklistTemplateService.getChecklistForTool(tool));
            
            // Load field definitions and custom field values
            if (tool.getToolType() != null) {
                String toolType = tool.getToolType().name();
                List<ToolTypeFieldDefinition> fieldDefinitions = fieldDefinitionService.getFieldDefinitionsForToolType(toolType);
                model.addAttribute("fieldDefinitions", fieldDefinitions);
                
                Map<String, String> customFields = toolService.getCustomFieldsForTool(tool.getId());
                model.addAttribute("customFields", customFields);
            }
            
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
            
            // Fetch NCSR parts associated with this tool
            List<NCSR> ncsrParts = ncsrService.getNCSRsForTool(id);
            model.addAttribute("ncsrParts", ncsrParts);
            model.addAttribute("ncsrCount", ncsrParts.size());
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
        
        // Add custom locations for the moving parts modal
        if (principal != null) {
            userService.getUserByEmail(principal.getName()).ifPresent(currentUser -> {
                if (currentUser.getActiveSite() != null) {
                    java.util.Map<com.pcd.manager.model.CustomLocation, Integer> customLocationsWithCounts = 
                        customLocationService.getCustomLocationsWithPartCounts(currentUser.getActiveSite());
                    model.addAttribute("customLocations", customLocationsWithCounts);
                }
            });
        }
        
        return "tools/details";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Optional<Tool> toolOpt = toolService.getToolById(id);
        if (toolOpt.isPresent()) {
            Tool tool = toolOpt.get();
            model.addAttribute("tool", tool);
            
            // Load field definitions and custom field values
            if (tool.getToolType() != null) {
                String toolType = tool.getToolType().name();
                List<ToolTypeFieldDefinition> fieldDefinitions = fieldDefinitionService.getFieldDefinitionsForToolType(toolType);
                model.addAttribute("fieldDefinitions", fieldDefinitions);
                
                Map<String, String> customFields = toolService.getCustomFieldsForTool(tool.getId());
                model.addAttribute("customFields", customFields);
            }
            
            model.addAttribute("locations", locationService.getAllLocations());
            
            if (tool.getToolType() == Tool.ToolType.AMATGASGUARD) {
                model.addAttribute("checklistItems", checklistTemplateService.getChecklistForTool(tool));
                return "tools/form-gasguard";
            }
            return "tools/form";
        }
        model.addAttribute("locations", locationService.getAllLocations());
        return "tools/form";
    }

    @PostMapping
    public String saveTool(
            @ModelAttribute Tool tool,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam Map<String, String> allParams) {
        
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
            
            // Auto-generate a primary name for AMAT GasGuard if not provided
            if ((tool.getName() == null || tool.getName().trim().isEmpty()) && tool.getToolType() == Tool.ToolType.AMATGASGUARD) {
                String sys = tool.getSystemName();
                String eqLoc = tool.getEquipmentLocation();
                StringBuilder gen = new StringBuilder();
                if (sys != null && !sys.trim().isEmpty()) gen.append(sys.trim());
                if (eqLoc != null && !eqLoc.trim().isEmpty()) {
                    if (gen.length() > 0) gen.append(" - ");
                    gen.append(eqLoc.trim());
                }
                String generatedName = gen.length() > 0 ? gen.toString() : "GasGuard";
                tool.setName(generatedName);
                // Use Config# or Model# as secondary name if available
                if (tool.getSecondaryName() == null || tool.getSecondaryName().trim().isEmpty()) {
                    if (tool.getConfigNumber() != null && !tool.getConfigNumber().trim().isEmpty()) {
                        tool.setSecondaryName(tool.getConfigNumber().trim());
                    } else if (tool.getModel1() != null && !tool.getModel1().trim().isEmpty()) {
                        tool.setSecondaryName(tool.getModel1().trim());
                    }
                }
            }

            // Set default location if none selected
            if (tool.getLocationName() == null || tool.getLocationName().trim().isEmpty()) {
                logger.info("No location selected, attempting to set default location");
                locationService.getDefaultLocation().ifPresent(location -> {
                    tool.setLocationName(location.getDisplayName() != null ? 
                                       location.getDisplayName() : location.getName());
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
                Tool savedTool = toolService.saveTool(tool);
                logger.info("Tool saved successfully with ID: {}, redirecting to tool list", savedTool.getId());
                
                // Extract and save custom fields
                if (savedTool.getToolType() != null) {
                    Map<String, String> customFields = new HashMap<>();
                    String toolType = savedTool.getToolType().name();
                    List<ToolTypeFieldDefinition> fieldDefinitions = fieldDefinitionService.getFieldDefinitionsForToolType(toolType);
                    
                    for (ToolTypeFieldDefinition def : fieldDefinitions) {
                        String fieldKey = def.getFieldKey();
                        String paramKey = "customFields[" + fieldKey + "]";
                        String value = allParams.get(paramKey);
                        
                        if (value != null) {
                            // Handle boolean checkbox - if not present, it's false
                            if (def.getFieldType() == ToolTypeFieldDefinition.FieldType.BOOLEAN) {
                                // Checkbox sends "on" when checked, or nothing when unchecked
                                customFields.put(fieldKey, "on".equals(value) ? "true" : "false");
                            } else {
                                customFields.put(fieldKey, value);
                            }
                        } else if (def.getFieldType() == ToolTypeFieldDefinition.FieldType.BOOLEAN) {
                            // Checkbox not checked
                            customFields.put(fieldKey, "false");
                        }
                    }
                    
                    if (!customFields.isEmpty()) {
                        toolService.saveCustomFields(savedTool.getId(), customFields);
                        logger.info("Saved {} custom fields for tool {}", customFields.size(), savedTool.getId());
                    }
                }
                
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
        logger.info("NEW CODE: Assigning tool ID: {} to current user using service method", id);
        
        // Get current authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String email = auth.getName();
            
            // Use service method to handle the assignment with proper session management
            boolean success = toolService.assignUserToTool(id, email);
            
            if (success) {
                logger.info("Successfully assigned tool {} to user {}", id, email);
            } else {
                logger.warn("Failed to assign tool {} to user {}", id, email);
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
            
            // Use service method to handle the unassignment with proper session management
            boolean success = toolService.unassignUserFromTool(id, email);
            
            if (success) {
                logger.info("Successfully unassigned tool {} from user {}", id, email);
            } else {
                logger.warn("Failed to unassign tool {} from user {}", id, email);
            }
        } else {
            logger.warn("No authenticated user found when trying to unassign tool");
        }
        
        return "redirect:/tools/" + id;
    }
    
    /**
     * Admin endpoint to unassign a specific user from a tool
     */
    @PostMapping("/{id}/admin-unassign")
    public String adminUnassignUserFromTool(@PathVariable Long id, @RequestParam String userEmail) {
        logger.info("Admin unassigning user {} from tool ID: {}", userEmail, id);
        
        // Get current authenticated user to check admin privileges
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String currentUserEmail = auth.getName();
            Optional<User> currentUserOpt = userService.getUserByEmail(currentUserEmail);
            
            // Check if current user is admin
            if (currentUserOpt.isPresent() && "ADMIN".equals(currentUserOpt.get().getRole())) {
                User currentUser = currentUserOpt.get();
                boolean success = toolService.unassignUserFromTool(id, userEmail);
                
                if (success) {
                    logger.info("Admin {} successfully unassigned tool {} from user {}", currentUserEmail, id, userEmail);
                } else {
                    logger.warn("Admin {} failed to unassign tool {} from user {}", currentUserEmail, id, userEmail);
                }
            } else {
                logger.warn("Non-admin user {} attempted to use admin unassign endpoint", currentUserEmail);
            }
        } else {
            logger.warn("No authenticated user found when trying to admin unassign tool");
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
            @RequestParam(value = "documentTag", required = false) String documentTag,
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
                    // Store document tag if provided
                    if (documentTag != null && !documentTag.trim().isEmpty()) {
                        tool.getDocumentTags().put(filePath, documentTag.trim());
                        logger.info("Added document: {} with tag: {} to tool {}", filePath, documentTag, tool.getId());
                    } else {
                        logger.info("Added document: {} to tool {}", filePath, tool.getId());
                    }
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
                                 @RequestParam(required = false) Boolean commissionComplete,
                                 @RequestParam(required = false) Boolean preSl1Complete,
                                 @RequestParam(required = false) Boolean sl1Complete,
                                 @RequestParam(required = false) Boolean mechanicalPreSl1Complete,
                                 @RequestParam(required = false) Boolean mechanicalPostSl1Complete,
                                 @RequestParam(required = false) Boolean specificInputFunctionalityComplete,
                                 @RequestParam(required = false) Boolean modesOfOperationComplete,
                                 @RequestParam(required = false) Boolean specificSoosComplete,
                                 @RequestParam(required = false) Boolean fieldServiceReportComplete,
                                 @RequestParam(required = false) Boolean certificateOfApprovalComplete,
                                 @RequestParam(required = false) Boolean turnedOverToCustomerComplete,
                                 @RequestParam(required = false) Boolean startUpSl03Complete,
                                 RedirectAttributes redirectAttributes) {
        
        try {
            logger.info("Updating checklist for tool ID: {}", id);
            
            Optional<Tool> toolOpt = toolService.getToolById(id);
            if (toolOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Tool not found");
                return "redirect:/tools";
            }
            
            Tool tool = toolOpt.get();
            
            // Update dates
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
            
            // Update completion checkboxes (independent of dates)
            tool.setCommissionComplete(commissionComplete);
            tool.setPreSl1Complete(preSl1Complete);
            tool.setSl1Complete(sl1Complete);
            tool.setMechanicalPreSl1Complete(mechanicalPreSl1Complete);
            tool.setMechanicalPostSl1Complete(mechanicalPostSl1Complete);
            tool.setSpecificInputFunctionalityComplete(specificInputFunctionalityComplete);
            tool.setModesOfOperationComplete(modesOfOperationComplete);
            tool.setSpecificSoosComplete(specificSoosComplete);
            tool.setFieldServiceReportComplete(fieldServiceReportComplete);
            tool.setCertificateOfApprovalComplete(certificateOfApprovalComplete);
            tool.setTurnedOverToCustomerComplete(turnedOverToCustomerComplete);
            tool.setStartUpSl03Complete(startUpSl03Complete);
            
            // If this is the first time any item gets checked for this tool, snapshot labels
            checklistTemplateService.snapshotLabelsIfFirstCheck(tool);
            
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

    @PostMapping("/analyze-excel")
    @ResponseBody
    public Map<String, Object> analyzeExcelForDuplicates(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("=== TOOL EXCEL ANALYSIS STARTED ===");
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

            // Analyze Excel file for duplicates
            Map<String, Object> analysisResult = toolService.analyzeExcelForDuplicates(file);
            response.putAll(analysisResult);
            response.put("success", true);
            
            logger.info("Excel analysis completed. {} duplicates found, {} valid rows", 
                       analysisResult.get("duplicateCount"), analysisResult.get("validCount"));
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid Excel format: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
        } catch (Exception e) {
            logger.error("Error analyzing Excel file: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Error analyzing Excel file: " + e.getMessage());
        }
        
        return response;
    }

    @PostMapping("/process-excel-with-duplicates")
    @ResponseBody
    public Map<String, Object> processExcelWithDuplicates(
            @RequestBody Map<String, Object> requestData,
            Principal principal) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("=== PROCESSING EXCEL WITH DUPLICATE RESOLUTIONS ===");
            
            List<Map<String, Object>> validRows = (List<Map<String, Object>>) requestData.get("validRows");
            List<Map<String, Object>> duplicateResolutions = (List<Map<String, Object>>) requestData.get("duplicateResolutions");
            
            if (validRows == null) validRows = new ArrayList<>();
            if (duplicateResolutions == null) duplicateResolutions = new ArrayList<>();
            
            String currentUserName = principal != null ? principal.getName() : "Unknown";
            
            // Process the Excel data with user's duplicate resolutions
            Map<String, Object> result = toolService.processExcelWithDuplicateResolutions(
                validRows, duplicateResolutions, currentUserName);
            
            response.putAll(result);
            
            logger.info("Excel processing completed. Result: {}", result);
            
        } catch (Exception e) {
            logger.error("Error processing Excel with duplicates: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Error processing Excel: " + e.getMessage());
        }
        
        return response;
    }

    @PostMapping("/upload-excel")
    @ResponseBody
    public Map<String, Object> uploadToolsFromExcel(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("=== TOOL EXCEL UPLOAD STARTED (LEGACY) ===");
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

            // Parse Excel file and create tools (legacy method - kept for backward compatibility)
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
            
            // Try to load from file system first (for latest version)
            Path filePath = Paths.get("src/main/resources/reference-documents", filename);
            if (Files.exists(filePath)) {
                logger.info("Serving reference document from file system: {}", filePath);
                Resource resource = new FileSystemResource(filePath);
                
                if (resource.exists() && resource.isReadable()) {
                    return createResponseForReferenceDocument(filename, resource);
                }
            }
            
            // Fallback to classpath if file system version doesn't exist
            String resourcePath = "/reference-documents/" + filename;
            InputStream resourceStream = getClass().getResourceAsStream(resourcePath);
            
            if (resourceStream == null) {
                logger.warn("Reference document not found in classpath or file system: {}", filename);
                return ResponseEntity.notFound().build();
            }
            
            logger.info("Serving reference document from classpath: {}", resourcePath);
            // Create resource from input stream
            Resource resource = new InputStreamResource(resourceStream);
            
            return createResponseForReferenceDocument(filename, resource);
                    
        } catch (Exception e) {
            logger.error("Error serving reference document: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Helper method to create the HTTP response for reference document downloads
     */
    private ResponseEntity<Resource> createResponseForReferenceDocument(String filename, Resource resource) {
        // Determine content type
        String contentType = "application/octet-stream";
        if (filename.toLowerCase().endsWith(".xlsx")) {
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (filename.toLowerCase().endsWith(".xls")) {
            contentType = "application/vnd.ms-excel";
        } else if (filename.toLowerCase().endsWith(".pdf")) {
            contentType = "application/pdf";
        } else if (filename.toLowerCase().endsWith(".docx")) {
            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (filename.toLowerCase().endsWith(".doc")) {
            contentType = "application/msword";
        }
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                       "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
    
    /**
     * API endpoint to get all tools for selection modal
     */
    @GetMapping("/api/list")
    @ResponseBody
    public List<Map<String, Object>> getToolsForApi() {
        try {
            logger.debug("Loading tools for API list");
            List<Object[]> toolData = toolRepository.findAllForAsyncListView();
            logger.debug("Found {} tools for API list", toolData.size());
            
            return toolData.stream().map(row -> {
                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("id", row[0]);
                toolMap.put("name", row[1] != null ? row[1].toString() : "");
                toolMap.put("secondaryName", row[2] != null ? row[2].toString() : null);
                toolMap.put("toolType", row[3] != null ? row[3].toString() : null);
                toolMap.put("serialNumber1", row[4] != null ? row[4].toString() : null);
                toolMap.put("serialNumber2", row[5] != null ? row[5].toString() : null);
                toolMap.put("model1", row[6] != null ? row[6].toString() : null);
                toolMap.put("model2", row[7] != null ? row[7].toString() : null);
                toolMap.put("status", row[8] != null ? row[8].toString() : null);
                toolMap.put("locationName", row[9] != null ? row[9].toString() : null);
                
                // Handle timestamps
                if (row[10] != null) {
                    java.sql.Timestamp createdTimestamp = (java.sql.Timestamp) row[10];
                    toolMap.put("createdAt", createdTimestamp.toLocalDateTime().toString());
                }
                if (row[11] != null) {
                    java.sql.Timestamp updatedTimestamp = (java.sql.Timestamp) row[11];
                    toolMap.put("updatedAt", updatedTimestamp.toLocalDateTime().toString());
                }
                
                return toolMap;
            }).collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.error("Error loading tools for API", e);
            return new ArrayList<>();
        }
    }

    /**
     * Helper method to safely convert java.sql.Date to LocalDate
     */
    private static java.time.LocalDate convertSqlDateToLocalDate(java.sql.Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }
    
    /**
     * Helper method to safely convert java.sql.Timestamp to LocalDateTime
     */
    private static java.time.LocalDateTime convertSqlTimestampToLocalDateTime(java.sql.Timestamp sqlTimestamp) {
        return sqlTimestamp != null ? sqlTimestamp.toLocalDateTime() : null;
    }
    
    /**
     * Update basic information for a tool (AJAX endpoint)
     */
    @PostMapping("/{id}/update-basic-info")
    @ResponseBody
    public Map<String, Object> updateBasicInfo(@PathVariable Long id,
                                               @RequestParam(required = false, defaultValue = "") String name,
                                               @RequestParam(required = false, defaultValue = "") String secondaryName,
                                               @RequestParam(required = false, defaultValue = "") String serialNumber1,
                                               @RequestParam(required = false, defaultValue = "") String serialNumber2,
                                               @RequestParam(required = false, defaultValue = "") String model1,
                                               @RequestParam(required = false, defaultValue = "") String model2,
                                               @RequestParam(required = false, defaultValue = "") String chemicalGasService,
                                               @RequestParam(required = false, defaultValue = "") String setDate) {
        try {
            logger.info("Updating tool {} - Name: '{}', SecondaryName: '{}'", id, name, secondaryName);
            
            Tool tool = toolService.getToolById(id)
                    .orElseThrow(() -> new RuntimeException("Tool not found"));
            
            logger.info("Before update - Tool name: '{}', secondaryName: '{}'", tool.getName(), tool.getSecondaryName());
            
            // Update fields
            tool.setName(name.isEmpty() ? null : name);
            tool.setSecondaryName(secondaryName.isEmpty() ? null : secondaryName);
            tool.setSerialNumber1(serialNumber1.isEmpty() ? null : serialNumber1);
            tool.setSerialNumber2(serialNumber2.isEmpty() ? null : serialNumber2);
            tool.setModel1(model1.isEmpty() ? null : model1);
            tool.setModel2(model2.isEmpty() ? null : model2);
            tool.setChemicalGasService(chemicalGasService.isEmpty() ? null : chemicalGasService);
            
            // Parse and set date
            if (setDate != null && !setDate.isEmpty()) {
                try {
                    tool.setSetDate(java.time.LocalDate.parse(setDate));
                } catch (Exception e) {
                    logger.warn("Invalid date format: {}", setDate);
                }
            } else {
                tool.setSetDate(null);
            }
            
            // Save the tool
            Tool savedTool = toolService.saveTool(tool);
            
            logger.info("After save - Tool name: '{}', secondaryName: '{}'", savedTool.getName(), savedTool.getSecondaryName());
            logger.info("Updated basic info for tool ID: {}", id);
            return Map.of("success", true, "message", "Tool updated successfully");
        } catch (Exception e) {
            logger.error("Error updating tool basic info", e);
            return Map.of("success", false, "message", "Error updating tool: " + e.getMessage());
        }
    }
} 