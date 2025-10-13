package com.pcd.manager.controller;

import com.pcd.manager.model.*;
import com.pcd.manager.service.*;
import com.pcd.manager.util.UploadUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.UrlResource;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.lang.StringBuilder;
import java.util.HashSet;
import java.util.Set;
import com.pcd.manager.service.FileTransferService;
import java.util.HashMap;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.service.PassdownService;
import java.time.LocalDate;
import com.pcd.manager.service.ExcelService;
import java.security.Principal;
import org.springframework.util.StringUtils;
import com.pcd.manager.model.User;
import com.pcd.manager.model.RmaComment;
import org.springframework.security.core.Authentication;
import com.pcd.manager.model.MovingPart;
import org.springframework.security.core.context.SecurityContextHolder;
import com.pcd.manager.service.TrackTrendService;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Arrays;
import java.util.Comparator;

@Controller
@RequestMapping("/rma")
public class RmaController {

    private static final Logger logger = LoggerFactory.getLogger(RmaController.class);

    private final RmaService rmaService;
    private final LocationService locationService;
    private final ToolService toolService;
    private final UserService userService;
    private final UploadUtils uploadUtils;
    private final FileTransferService fileTransferService;
    private final PassdownService passdownService;
    private final ExcelService excelService;
    private final MovingPartService movingPartService;
    private final TrackTrendService trackTrendService;
    private final CustomLocationService customLocationService;
    
    // Repository dependencies for optimized queries
    private final com.pcd.manager.repository.RmaRepository rmaRepository;
    private final com.pcd.manager.repository.RmaCommentRepository rmaCommentRepository;
    private final com.pcd.manager.repository.MovingPartRepository movingPartRepository;

    @Autowired
    public RmaController(RmaService rmaService,
                         LocationService locationService,
                         ToolService toolService,
                         UserService userService,
                         UploadUtils uploadUtils,
                         FileTransferService fileTransferService,
                         PassdownService passdownService,
                         ExcelService excelService,
                         MovingPartService movingPartService,
                         TrackTrendService trackTrendService,
                         CustomLocationService customLocationService,
                         com.pcd.manager.repository.RmaRepository rmaRepository,
                         com.pcd.manager.repository.RmaCommentRepository rmaCommentRepository,
                         com.pcd.manager.repository.MovingPartRepository movingPartRepository) {
        this.rmaService = rmaService;
        this.locationService = locationService;
        this.toolService = toolService;
        this.userService = userService;
        this.uploadUtils = uploadUtils;
        this.fileTransferService = fileTransferService;
        this.passdownService = passdownService;
        this.excelService = excelService;
        this.movingPartService = movingPartService;
        this.trackTrendService = trackTrendService;
        this.customLocationService = customLocationService;
        this.rmaRepository = rmaRepository;
        this.rmaCommentRepository = rmaCommentRepository;
        this.movingPartRepository = movingPartRepository;
    }

    @GetMapping
    public String listRmas(Model model) {
        logger.info("=== LOADING RMA LIST PAGE (INSTANT) ===");
        
        // Return empty data for instant page load - data will be loaded asynchronously
        model.addAttribute("rmas", new ArrayList<>());
        model.addAttribute("rmaCommentsMap", new HashMap<>());
        model.addAttribute("movingPartsMap", new HashMap<>());
        model.addAttribute("asyncLoading", true); // Flag to indicate async loading is enabled
        
        logger.info("=== RMA LIST PAGE LOADED INSTANTLY ===");
        return "rma/list";
    }

    /**
     * Generate Returned Goods Decontamination Certificate PDF for an RMA
     */
    @GetMapping("/{id}/decon-cert.pdf")
    public ResponseEntity<byte[]> downloadDeconCertificate(@PathVariable Long id) {
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            Rma rma = rmaOpt.get();

            byte[] pdf = rmaService.generateDeconCertificate(rma);

            String filename = "RMA_" + (rma.getReferenceNumber() != null ? rma.getReferenceNumber() : id) + "_Decon_Cert.pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            logger.error("Error generating decon certificate for RMA {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    /**
     * Generate Returned Goods Label PDF for an RMA
     */
    @GetMapping("/{id}/return-label.pdf")
    public ResponseEntity<byte[]> downloadReturnLabel(@PathVariable Long id) {
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            Rma rma = rmaOpt.get();
            byte[] pdf = rmaService.generateReturnLabel(rma);
            String filename = "RMA_" + (rma.getReferenceNumber() != null ? rma.getReferenceNumber() : id) + "_Return_Label.pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            logger.error("Error generating return label for RMA {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Async endpoint for loading RMA data - OPTIMIZED VERSION
     */
    @GetMapping("/api/data")
    @ResponseBody
    public Map<String, Object> loadRmaDataAsync() {
        logger.info("=== LOADING RMA DATA ASYNC (OPTIMIZED) ===");
        long startTime = System.currentTimeMillis();
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Use optimized query that fetches only required fields in a single query
            List<Object[]> rmaRows = rmaRepository.findAllForAsyncListView();
            logger.info("Loaded {} RMA rows with optimized query", rmaRows.size());
            
            List<Map<String, Object>> rmaData = new ArrayList<>();
            List<Long> rmaIds = new ArrayList<>();
            
            // Process the optimized query results
            for (Object[] row : rmaRows) {
                Long rmaId = (Long) row[0];
                rmaIds.add(rmaId);
                
                Map<String, Object> rmaMap = new HashMap<>();
                rmaMap.put("id", rmaId);
                // Store the reference number as both rmaNumber and sapNotificationNumber for backward compatibility
                String referenceNumber = (String) row[1];
                rmaMap.put("rmaNumber", referenceNumber);
                rmaMap.put("sapNotificationNumber", referenceNumber);
                rmaMap.put("status", row[2] != null ? row[2].toString() : null);
                rmaMap.put("statusDisplayName", row[2] != null ? ((RmaStatus) row[2]).getDisplayName() : null);
                rmaMap.put("priority", row[3] != null ? ((RmaPriority) row[3]).name() : "MEDIUM");
                rmaMap.put("priorityDisplayName", row[3] != null ? ((RmaPriority) row[3]).getDisplayName() : "Medium");
                rmaMap.put("customerName", (String) row[4]);
                rmaMap.put("writtenDate", row[5] != null ? row[5].toString() : null);
                rmaMap.put("rmaNumberProvidedDate", row[6] != null ? row[6].toString() : null);
                rmaMap.put("shippingMemoEmailedDate", row[7] != null ? row[7].toString() : null);
                rmaMap.put("partsReceivedDate", row[8] != null ? row[8].toString() : null);
                rmaMap.put("installedPartsDate", row[9] != null ? row[9].toString() : null);
                rmaMap.put("failedPartsPackedDate", row[10] != null ? row[10].toString() : null);
                rmaMap.put("failedPartsShippedDate", row[11] != null ? row[11].toString() : null);
                
                // Handle timestamp fields (row[12] = createdDate, row[13] = updatedAt)
                if (row[12] != null) {
                    rmaMap.put("createdDate", row[12].toString());
                } else {
                    rmaMap.put("createdDate", null);
                }
                if (row[13] != null) {
                    rmaMap.put("updatedAt", row[13].toString());
                } else {
                    rmaMap.put("updatedAt", null);
                }
                
                // Handle tool data (row[14] = tool.id, row[15] = tool.name)
                if (row[14] != null) {
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("id", (Long) row[14]);
                    toolMap.put("name", row[15] != null ? (String) row[15] : "");
                    rmaMap.put("tool", toolMap);
                } else {
                    rmaMap.put("tool", null);
                }
                
                // Handle location data (row[16] = location.id, row[17] = location.name)
                if (row[16] != null) {
                    Map<String, Object> locationMap = new HashMap<>();
                    locationMap.put("id", (Long) row[16]);
                    locationMap.put("name", row[17] != null ? (String) row[17] : "");
                    rmaMap.put("location", locationMap);
                } else {
                    rmaMap.put("location", null);
                }
                
                // Handle problem details for tooltip (row[18-23])
                rmaMap.put("problemDiscoverer", (String) row[18]);
                rmaMap.put("problemDiscoveryDate", row[19] != null ? row[19].toString() : null);
                rmaMap.put("whatHappened", (String) row[20]);
                rmaMap.put("whyAndHowItHappened", (String) row[21]);
                rmaMap.put("howContained", (String) row[22]);
                rmaMap.put("whoContained", (String) row[23]);
                
                rmaData.add(rmaMap);
            }
            
            // Bulk load part line items for all RMAs
            Map<Long, List<Map<String, Object>>> partLineItemsMap = new HashMap<>();
            if (!rmaIds.isEmpty()) {
                try {
                    List<Object[]> partRows = rmaRepository.findPartLineItemsByRmaIds(rmaIds);
                    for (Object[] partRow : partRows) {
                        Long rmaId = (Long) partRow[0];
                        Map<String, Object> partMap = new HashMap<>();
                        partMap.put("partName", (String) partRow[1]);
                        partMap.put("partNumber", (String) partRow[2]);
                        partMap.put("productDescription", (String) partRow[3]);
                        
                        partLineItemsMap.computeIfAbsent(rmaId, k -> new ArrayList<>()).add(partMap);
                    }
                    logger.info("Loaded part line items for {} RMAs", partLineItemsMap.size());
                } catch (Exception e) {
                    logger.error("Error loading part line items: {}", e.getMessage(), e);
                }
            }
            
            // Load only comment counts (not content) for performance
            Map<Long, Integer> rmaCommentsMap = new HashMap<>();
            if (!rmaIds.isEmpty()) {
                try {
                    // Load comment counts only - content will be loaded on demand
                    List<Object[]> commentCounts = rmaCommentRepository.findCommentCountsByRmaIds(rmaIds);
                    for (Object[] row : commentCounts) {
                        Long rmaId = (Long) row[0];
                        Long count = (Long) row[1];
                        rmaCommentsMap.put(rmaId, count.intValue());
                    }
                    logger.info("Loaded comment counts for {} RMAs (content will be lazy-loaded)", commentCounts.size());
                } catch (Exception e) {
                    logger.error("Error loading RMA comment counts: {}", e.getMessage(), e);
                }
            }
            
            // Bulk load moving parts data (not just counts) for tooltip display
            Map<Long, List<Map<String, Object>>> movingPartsMap = new HashMap<>();
            if (!rmaIds.isEmpty()) {
                try {
                    // Get all moving parts for these RMAs
                    List<MovingPart> allMovingParts = movingPartService.getMovingPartsByRmaIds(rmaIds);
                    
                    for (MovingPart part : allMovingParts) {
                        Long rmaId = part.getRma() != null ? part.getRma().getId() : null;
                        if (rmaId != null) {
                            Map<String, Object> partData = new HashMap<>();
                            partData.put("id", part.getId());
                            partData.put("partName", part.getPartName());
                            partData.put("partNumber", part.getPartNumber());
                            partData.put("fromLocation", part.getFromTool() != null ? part.getFromTool().getName() : "Unknown");
                            
                            // Get destination from destination chain (use the last destination as current location)
                            Long currentLocationId = part.getCurrentLocationToolId();
                            String toLocation = "Unknown";
                            if (currentLocationId != null) {
                                try {
                                    Optional<Tool> destTool = toolService.getToolById(currentLocationId);
                                    toLocation = destTool.map(Tool::getName).orElse("Unknown");
                                } catch (Exception e) {
                                    logger.warn("Could not fetch destination tool for ID: {}", currentLocationId);
                                }
                            }
                            partData.put("toLocation", toLocation);
                            partData.put("movementDate", part.getMoveDate() != null ? part.getMoveDate().toString() : null);
                            partData.put("notes", part.getNotes());
                            
                            movingPartsMap.computeIfAbsent(rmaId, k -> new ArrayList<>()).add(partData);
                        }
                    }
                    logger.info("Loaded moving parts data for {} RMAs", movingPartsMap.size());
                } catch (Exception e) {
                    logger.error("Error loading RMA moving parts data: {}", e.getMessage(), e);
                }
            }
            
            // Add part line items to RMA data (comments will be loaded on demand)
            for (Map<String, Object> rmaMap : rmaData) {
                Long rmaId = (Long) rmaMap.get("id");
                rmaMap.put("partLineItems", partLineItemsMap.getOrDefault(rmaId, new ArrayList<>()));
                // Comments are no longer pre-loaded - they'll be fetched on hover
            }
            
            result.put("success", true);
            result.put("rmas", rmaData);
            result.put("rmaCommentsMap", rmaCommentsMap);
            result.put("movingPartsMap", movingPartsMap);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("=== COMPLETED OPTIMIZED ASYNC RMA DATA LOADING in {}ms ===", duration);
            logger.info("Final counts - RMAs: {}, Comments: {}, Moving Parts: {}", 
                       rmaData.size(),
                       rmaCommentsMap.values().stream().mapToInt(Integer::intValue).sum(),
                       movingPartsMap.values().stream().mapToInt(List::size).sum());
                       
        } catch (Exception e) {
            logger.error("Error loading RMA data async: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    @GetMapping("/test-upload")
    public String testUpload() {
        return "test-upload";
    }

    @GetMapping("/matrix")
    public String showRmaMatrix(Model model) {
        List<Rma> allRmas = rmaService.getAllRmas();
        
        // Convert RMAs to map grouped by status
        Map<RmaStatus, List<Rma>> rmasByStatus = allRmas.stream()
                .collect(Collectors.groupingBy(rma -> rma.getStatus()));
        
        // Ensure all statuses are represented in the map
        Map<RmaStatus, List<Rma>> matrix = new EnumMap<>(RmaStatus.class);
        for (RmaStatus status : RmaStatus.values()) {
            matrix.put(status, rmasByStatus.getOrDefault(status, List.of()));
        }
        
        model.addAttribute("matrix", matrix);
        model.addAttribute("statuses", RmaStatus.values());
        
        return "rma/matrix";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model, @RequestParam(required = false) Long toolId) {
        Rma rma = new Rma();
        
        // Get the currently logged-in user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserName = null;
        
        if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            String username = authentication.getName(); // Username (email) of the logged-in user
            logger.info("Current authenticated user: {}", username);
            
            Optional<User> currentUser = userService.getUserByUsername(username);
            if (currentUser.isPresent()) {
                User user = currentUser.get();
                
                // Store user name for the view
                if (user.getName() != null && !user.getName().trim().isEmpty()) {
                    currentUserName = user.getName().trim();
                } else if (user.getFirstName() != null && !user.getFirstName().trim().isEmpty()) {
                    StringBuilder nameBuilder = new StringBuilder(user.getFirstName().trim());
                    if (user.getLastName() != null && !user.getLastName().trim().isEmpty()) {
                        nameBuilder.append(" ").append(user.getLastName().trim());
                    }
                    currentUserName = nameBuilder.toString();
                } else {
                    currentUserName = username;
                }
                
                logger.info("Found currentUserName: {}", currentUserName);
                
                // Pre-fill technician info from current user
                rma.setFieldTechName(currentUserName);
                if (user.getPhoneNumber() != null) {
                    rma.setFieldTechPhone(user.getPhoneNumber());
                }
                if (user.getEmail() != null) {
                    rma.setFieldTechEmail(user.getEmail());
                }
                
                // Set location with priority order:
                // 1. User's active site (this is what we're adding/prioritizing)
                // 2. User's default location 
                // 3. User's active tool's location
                Location selectedLocation = null;
                if (user.getActiveSite() != null) {
                    selectedLocation = user.getActiveSite();
                    rma.setLocation(selectedLocation);
                    logger.info("Set default location to user's active site: {}", selectedLocation.getDisplayName());
                } else if (user.getDefaultLocation() != null) {
                    selectedLocation = user.getDefaultLocation();
                    rma.setLocation(selectedLocation);
                    logger.info("Set default location to user's default location: {}", selectedLocation.getDisplayName());
                } else if (user.getActiveTool() != null && user.getActiveTool().getLocationName() != null) {
                    // Find the Location object by name from the tool's location name
                    Optional<Location> toolLocation = locationService.getLocationByName(user.getActiveTool().getLocationName());
                    if (toolLocation.isPresent()) {
                        selectedLocation = toolLocation.get();
                        rma.setLocation(selectedLocation);
                        logger.info("Set default location to user's active tool location: {}", user.getActiveTool().getLocationName());
                    }
                } else {
                    // If no user-specific location, try to get the system default location
                    Optional<Location> defaultLocation = locationService.getDefaultLocation();
                    if (defaultLocation.isPresent()) {
                        selectedLocation = defaultLocation.get();
                        rma.setLocation(selectedLocation);
                        logger.info("Set default location to system default: {}", selectedLocation.getDisplayName());
                    } else {
                        logger.warn("No default location found for RMA form");
                    }
                }
                
                // Pre-fill customer info from location if available
                if (selectedLocation != null) {
                    if (selectedLocation.getCustomerName() != null) {
                        rma.setCustomerName(selectedLocation.getCustomerName());
                    }
                    if (selectedLocation.getCustomerPhone() != null) {
                        rma.setCustomerPhone(selectedLocation.getCustomerPhone());
                    }
                    if (selectedLocation.getCustomerEmail() != null) {
                        rma.setCustomerEmail(selectedLocation.getCustomerEmail());
                    }
                    
                    // Pre-fill shipping address
                    if (selectedLocation.getShipToName() != null) {
                        rma.setCompanyShipToName(selectedLocation.getShipToName());
                    }
                    if (selectedLocation.getShipToAddress() != null) {
                        rma.setCompanyShipToAddress(selectedLocation.getShipToAddress());
                    }
                    if (selectedLocation.getShipToCity() != null) {
                        rma.setCity(selectedLocation.getShipToCity());
                    }
                    if (selectedLocation.getShipToState() != null) {
                        rma.setState(selectedLocation.getShipToState());
                    }
                    if (selectedLocation.getShipToZip() != null) {
                        rma.setZipCode(selectedLocation.getShipToZip());
                    }
                    if (selectedLocation.getShipToAttn() != null) {
                        rma.setAttn(selectedLocation.getShipToAttn());
                    }
                    
                    logger.info("Pre-filled customer info and shipping address from location defaults");
                }
            } else {
                logger.warn("Could not find user with username: {}", username);
            }
        } else {
            logger.warn("No authenticated user found or user is anonymous");
            
            // Try to set a default location from the system settings
            Optional<Location> defaultLocation = locationService.getDefaultLocation();
            if (defaultLocation.isPresent()) {
                rma.setLocation(defaultLocation.get());
                logger.info("Set default location to system default for anonymous user: {}", defaultLocation.get().getDisplayName());
            }
        }
        
        // If toolId is provided, fetch and set the tool
        if (toolId != null) {
            logger.info("Tool ID {} provided for new RMA", toolId);
            toolService.getToolById(toolId).ifPresent(tool -> {
                rma.setTool(tool);
                // If we have a tool, use its location only if we don't already have a location from the user
                if (tool.getLocationName() != null && rma.getLocation() == null) {
                    // Find the Location object by name from the tool's location name
                    Optional<Location> toolLocation = locationService.getLocationByName(tool.getLocationName());
                    if (toolLocation.isPresent()) {
                        rma.setLocation(toolLocation.get());
                        logger.info("Set location from provided tool: {}", tool.getLocationName());
                    }
                }
                logger.info("Pre-selected tool {} for new RMA", tool.getName());
            });
        }
        
        model.addAttribute("rma", rma);
        model.addAttribute("locations", locationService.getAllLocations());
        
        // Get and sort tools alphabetically
        List<Tool> tools = toolService.getAllTools();
        tools.sort((t1, t2) -> {
            String name1 = t1.getName() != null ? t1.getName().toLowerCase() : "";
            String name2 = t2.getName() != null ? t2.getName().toLowerCase() : "";
            return name1.compareTo(name2);
        });
        model.addAttribute("tools", tools);
        
        model.addAttribute("technicians", userService.getAllUsers());
        
        // Add current user name to the model for use in the form
        model.addAttribute("currentUserName", currentUserName);
        
        // Add custom locations for the moving parts modal
        if (authentication != null && authentication.getName() != null) {
            userService.getUserByEmail(authentication.getName()).ifPresent(user -> {
                if (user.getActiveSite() != null) {
                    Map<CustomLocation, Integer> customLocationsWithCounts = 
                        customLocationService.getCustomLocationsWithPartCounts(user.getActiveSite());
                    model.addAttribute("customLocations", customLocationsWithCounts);
                }
            });
        }
        
        return "rma/form";
    }

    @GetMapping("/{id}")
    public String showRma(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        logger.info("Showing RMA page for ID: {}", id);
        Optional<Rma> rmaOpt = rmaService.getRmaById(id);
        
        if (!rmaOpt.isPresent()) {
            logger.warn("RMA not found with ID: {}", id);
            redirectAttributes.addFlashAttribute("error", "RMA not found with ID: " + id);
            return "redirect:/rma";
        }
        
        Rma rma = rmaOpt.get();
        
        // Add all necessary model attributes (lean data only; heavy sections are lazy-loaded)
        model.addAttribute("rma", rma);
        // Ensure comments are available for rendering on the detail page
        try {
            List<RmaComment> rmaComments = rmaService.getCommentsForRma(id);
            model.addAttribute("rmaComments", rmaComments);
        } catch (Exception e) {
            logger.error("Failed to load comments for RMA {}: {}", id, e.getMessage(), e);
            model.addAttribute("rmaComments", java.util.Collections.emptyList());
        }
        // Provide initial lightweight counts so badges can render quickly
        try {
            Map<String, Integer> counts = rmaService.getRmaCounts(id);
            model.addAttribute("rmaCounts", counts);
        } catch (Exception ignore) {}
        model.addAttribute("allRmas", rmaService.getAllRmas());
        model.addAttribute("locations", locationService.getAllLocations());
        model.addAttribute("technicians", userService.getAllUsers());
        
        // Generate HTML options for tools for JavaScript use
        StringBuilder toolOptionsHtml = new StringBuilder();
        List<Tool> allTools = toolService.getAllTools();
        // Sort tools alphabetically by name
        allTools.sort((t1, t2) -> {
            String name1 = t1.getName() != null ? t1.getName().toLowerCase() : "";
            String name2 = t2.getName() != null ? t2.getName().toLowerCase() : "";
            return name1.compareTo(name2);
        });
        for (Tool tool : allTools) {
            String displayName = tool.getName();
            if (tool.getSecondaryName() != null && !tool.getSecondaryName().isEmpty()) {
                displayName += " (" + tool.getSecondaryName() + ")";
            }
            toolOptionsHtml.append("<option value=\"").append(tool.getId()).append("\">")
                           .append(displayName).append("</option>");
        }
        model.addAttribute("allToolsOptions", toolOptionsHtml.toString());
        model.addAttribute("allTools", allTools);
        
        // Add custom locations for the moving parts modal
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            userService.getUserByEmail(auth.getName()).ifPresent(currentUser -> {
                if (currentUser.getActiveSite() != null) {
                    Map<CustomLocation, Integer> customLocationsWithCounts = 
                        customLocationService.getCustomLocationsWithPartCounts(currentUser.getActiveSite());
                    model.addAttribute("customLocations", customLocationsWithCounts);
                }
            });
        }
        
        // Add moving parts and their destination chains
        List<MovingPart> movingParts = movingPartService.getMovingPartsByRmaId(id);
        model.addAttribute("movingParts", movingParts);
        
        // Create a map of moving part IDs to their destination chains
        Map<Long, List<Tool>> movingPartDestinationChains = new HashMap<>();
        for (MovingPart part : movingParts) {
            List<Tool> chain = movingPartService.getDestinationChainForMovingPart(part.getId());
            movingPartDestinationChains.put(part.getId(), chain);
        }
        model.addAttribute("movingPartDestinationChains", movingPartDestinationChains);

        // Track/Trend associations (by connected tool)
        List<TrackTrend> relatedTrackTrends = new ArrayList<>();
        if (rma.getTool() != null) {
            relatedTrackTrends = trackTrendService.getTrackTrendsByToolId(rma.getTool().getId());
        }
        model.addAttribute("trackTrends", relatedTrackTrends);
        model.addAttribute("allTrackTrends", trackTrendService.getAllTrackTrends());
        
        return "rma/view";
    }

    /**
     * Lazy JSON endpoints for heavy sections
     */
    @GetMapping("/{id}/api/counts")
    @ResponseBody
    public Map<String, Integer> getCounts(@PathVariable Long id) {
        return rmaService.getRmaCounts(id);
    }

    @GetMapping("/{id}/api/documents")
    @ResponseBody
    public List<?> getDocuments(@PathVariable Long id) {
        return rmaService.getRmaByIdInitialized(id)
                .map(r -> r.getDocuments() == null ? List.of() : r.getDocuments().stream().map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", d.getId());
                    m.put("fileName", d.getFileName());
                    m.put("filePath", d.getFilePath());
                    return m;
                }).toList())
                .orElse(List.of());
    }

    @GetMapping("/{id}/api/pictures")
    @ResponseBody
    public List<?> getPictures(@PathVariable Long id) {
        return rmaService.getRmaByIdInitialized(id)
                .map(r -> r.getPictures() == null ? List.of() : r.getPictures().stream().map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.getId());
                    m.put("fileName", p.getFileName());
                    m.put("filePath", p.getFilePath());
                    return m;
                }).toList())
                .orElse(List.of());
    }

    @GetMapping("/{id}/api/comments")
    @ResponseBody
    public List<?> getComments(@PathVariable Long id) {
        return rmaService.getRmaByIdInitialized(id)
                .map(r -> r.getComments() == null ? List.of() : r.getComments().stream().map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("content", c.getContent());
                    m.put("createdDate", c.getCreatedDate());
                    m.put("author", c.getUser() != null ? c.getUser().getName() : null);
                    return m;
                }).toList())
                .orElse(List.of());
    }

    @GetMapping("/{id}/api/parts")
    @ResponseBody
    public List<?> getParts(@PathVariable Long id) {
        return rmaService.getRmaByIdInitialized(id)
                .map(r -> r.getPartLineItems() == null ? List.of() : r.getPartLineItems().stream().map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("partName", p.getPartName());
                    m.put("partNumber", p.getPartNumber());
                    m.put("productDescription", p.getProductDescription());
                    m.put("quantity", p.getQuantity());
                    return m;
                }).toList())
                .orElse(List.of());
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, 
                               @RequestParam(required = false, defaultValue = "false") boolean importExcel,
                               Model model) {
        rmaService.getRmaById(id).ifPresent(rma -> model.addAttribute("rma", rma));
        model.addAttribute("locations", locationService.getAllLocations());
        
        // Get and sort tools alphabetically
        List<Tool> tools = toolService.getAllTools();
        tools.sort((t1, t2) -> {
            String name1 = t1.getName() != null ? t1.getName().toLowerCase() : "";
            String name2 = t2.getName() != null ? t2.getName().toLowerCase() : "";
            return name1.compareTo(name2);
        });
        model.addAttribute("tools", tools);
        
        model.addAttribute("technicians", userService.getAllUsers());
        model.addAttribute("importExcel", importExcel);
        
        // Add all RMAs for transfer functionality
        model.addAttribute("allRmas", rmaService.getAllRmas());
        
        // Add moving parts associated with this RMA
        List<MovingPart> movingParts = movingPartService.getMovingPartsByRmaId(id);
        model.addAttribute("movingParts", movingParts);
        
        // Add custom locations for the moving parts modal
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            userService.getUserByEmail(auth.getName()).ifPresent(currentUser -> {
                if (currentUser.getActiveSite() != null) {
                    Map<CustomLocation, Integer> customLocationsWithCounts = 
                        customLocationService.getCustomLocationsWithPartCounts(currentUser.getActiveSite());
                    model.addAttribute("customLocations", customLocationsWithCounts);
                }
            });
        }
        
        return "rma/form";
    }

    @PostMapping("/parse-excel")
    @ResponseBody
    public Map<String, Object> parseExcelFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>(); // Initialize response here
        
        try {
            logger.info("=== EXCEL FILE PARSING STARTED ===");
            logger.info("Received file: {}, size: {} bytes, content type: {}", 
                file.getOriginalFilename(), file.getSize(), file.getContentType());
            
            if (file.isEmpty()) {
                logger.warn("Uploaded file is empty");
                response.put("error", "Please select a file to upload");
                return response;
            }

            // Create a temporary RMA to store the file
            Rma tempRma = new Rma();
            tempRma.setDocuments(new ArrayList<>());
            
            logger.info("Saving Excel file as temporary document...");
            RmaDocument excelDoc = uploadUtils.saveRmaDocument(tempRma, file);
            
            if (excelDoc == null) {
                logger.error("Failed to save Excel file as document");
                response.put("error", "Failed to save Excel file");
                return response;
            }
            
            logger.info("Excel file saved successfully:");
            logger.info("  - File Name: {}", excelDoc.getFileName());
            logger.info("  - File Path: {}", excelDoc.getFilePath());
            logger.info("  - File Type: {}", excelDoc.getFileType());
            logger.info("  - File Size: {} bytes", excelDoc.getFileSize());

            logger.info("Parsing Excel file from path: {}", excelDoc.getFilePath());
            Map<String, Object> extractedData = excelService.extractRmaDataFromExcelFile(excelDoc.getFilePath());
            
            if (extractedData.containsKey("error")) {
                logger.error("Failed to parse Excel file: {}", extractedData.get("error"));
                uploadUtils.deleteFile(excelDoc.getFilePath());
                response.putAll(extractedData); // Put the error message in the response
                return response;
            }

            // Initialize response with all successfully extracted data
            response.putAll(extractedData);

            // Attempt to find a matching tool using parsed serial numbers
            String parsedSerial1 = (String) extractedData.get("parsedSerial1");
            String parsedSerial2 = (String) extractedData.get("parsedSerial2");
            logger.info("Extracted from Excel -> parsedSerial1: '{}', parsedSerial2: '{}'", parsedSerial1, parsedSerial2);
            Long matchedToolId = null;

            if (parsedSerial1 != null && !parsedSerial1.isEmpty()) {
                logger.info("Attempting to find tool by parsedSerial1: {}", parsedSerial1);
                // Assuming toolService uses toolRepository.findBySerialNumber1()
                Optional<Tool> toolOpt = toolService.findToolBySerialNumber(parsedSerial1); 
                if (toolOpt.isPresent()) {
                    matchedToolId = toolOpt.get().getId();
                    logger.info("Found matching tool by serial number1: ID={}, Name={}", matchedToolId, toolOpt.get().getName());
                }
            }

            if (matchedToolId == null && parsedSerial2 != null && !parsedSerial2.isEmpty()) {
                logger.info("Attempting to find tool by parsedSerial2: {}", parsedSerial2);
                Optional<Tool> toolOpt = toolService.findToolBySerialNumber2(parsedSerial2); // Changed to findToolBySerialNumber2
                if (toolOpt.isPresent()) {
                    matchedToolId = toolOpt.get().getId();
                    logger.info("Found matching tool by serial number2: ID={}, Name={}", matchedToolId, toolOpt.get().getName());
                }
            }

            if (matchedToolId != null) {
                response.put("matchedToolId", matchedToolId);
            }

            // Add the document info to the response AFTER potential matchedToolId
            response.put("document", Map.of(
                "fileName", excelDoc.getFileName(),
                "filePath", excelDoc.getFilePath(),
                "fileType", excelDoc.getFileType(),
                "fileSize", excelDoc.getFileSize()
            ));

            logger.info("Excel file parsed successfully, response prepared.");
            return response;
        } catch (Exception e) {
            logger.error("Error processing Excel file: {}", e.getMessage(), e);
            response.put("error", "Error processing Excel file: " + e.getMessage());
            return response;
        }
    }

    @PostMapping("/save")
    public String saveRma(@ModelAttribute Rma rma,
                         @RequestParam(value = "documentUploads", required = false) MultipartFile[] documentUploads,
                         @RequestParam(value = "imageUploads", required = false) MultipartFile[] imageUploads,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            // Debug logging
            logger.info("=== RMA SAVE REQUEST DEBUG ===");
            logger.info("Request Method: {}", request.getMethod());
            logger.info("Content Type: {}", request.getContentType());
            logger.info("documentUploads param exists: {}", documentUploads != null);
            logger.info("imageUploads param exists: {}", imageUploads != null);
            if (documentUploads != null) {
                logger.info("documentUploads length: {}", documentUploads.length);
                for (int i = 0; i < documentUploads.length; i++) {
                    MultipartFile f = documentUploads[i];
                    logger.info("documentUploads[{}]: empty={}, name={}, size={}", 
                        i, f.isEmpty(), f.getOriginalFilename(), f.getSize());
                }
            }
            if (imageUploads != null) {
                logger.info("imageUploads length: {}", imageUploads.length);
                for (int i = 0; i < imageUploads.length; i++) {
                    MultipartFile f = imageUploads[i];
                    logger.info("imageUploads[{}]: empty={}, name={}, size={}", 
                        i, f.isEmpty(), f.getOriginalFilename(), f.getSize());
                }
            }
            
            // Log all request parameters
            logger.info("All request parameter names:");
            request.getParameterNames().asIterator().forEachRemaining(name -> {
                logger.info("  {} = {}", name, request.getParameter(name));
            });
            logger.info("=== END DEBUG ===");
            
            logger.info("=== SAVING RMA ===");
            // Combine all file uploads into one list
            List<MultipartFile> allFiles = new ArrayList<>();
            
            // Handle document uploads
            if (documentUploads != null) {
                for (MultipartFile file : documentUploads) {
                    if (file != null && !file.isEmpty()) {
                        String contentType = file.getContentType();
                        logger.info("Processing document upload: {}, size: {}, type: {}", 
                            file.getOriginalFilename(), file.getSize(), contentType);
                        
                        // Check if this is an Excel file
                        boolean isExcelFile = (contentType != null && 
                            (contentType.equals("application/vnd.ms-excel") || 
                             contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))) ||
                            (file.getOriginalFilename() != null && 
                             (file.getOriginalFilename().toLowerCase().endsWith(".xlsx") || 
                              file.getOriginalFilename().toLowerCase().endsWith(".xls")));
                              
                        if (isExcelFile) {
                            logger.info("=== EXCEL FILE DETECTED ===");
                            logger.info("Found Excel file in document uploads: {}", file.getOriginalFilename());
                            
                            // Check if this Excel file was already processed during parsing
                            String excelFileIncluded = request.getParameter("excelFileIncluded");
                            boolean wasAlreadyParsed = "true".equals(excelFileIncluded);
                            
                            if (wasAlreadyParsed) {
                                logger.info("Excel file was already parsed and saved during parsing phase - will be attached to RMA");
                                
                                // Get the document info from the parsed Excel response stored in JS
                                // Look for document information in the request parameters
                                String documentFileName = request.getParameter("parsedExcelFileName");
                                String documentFilePath = request.getParameter("parsedExcelFilePath");
                                String documentFileType = request.getParameter("parsedExcelFileType");
                                String documentFileSize = request.getParameter("parsedExcelFileSize");
                                
                                if (documentFileName != null && documentFilePath != null) {
                                    logger.info("Attaching previously parsed Excel document to RMA");
                                    RmaDocument excelDoc = new RmaDocument();
                                    excelDoc.setFileName(documentFileName);
                                    excelDoc.setFilePath(documentFilePath);
                                    excelDoc.setFileType(documentFileType);
                                    excelDoc.setFileSize(documentFileSize != null ? Long.parseLong(documentFileSize) : file.getSize());
                                    excelDoc.setRma(rma);
                                    
                                    if (rma.getDocuments() == null) {
                                        rma.setDocuments(new ArrayList<>());
                                        logger.info("Initialized documents collection for RMA");
                                    }
                                    rma.getDocuments().add(excelDoc);
                                    logger.info("Successfully attached parsed Excel document:");
                                    logger.info("  - File Name: {}", excelDoc.getFileName());
                                    logger.info("  - File Path: {}", excelDoc.getFilePath());
                                    logger.info("  - File Type: {}", excelDoc.getFileType());
                                    logger.info("  - File Size: {} bytes", excelDoc.getFileSize());
                                } else {
                                    logger.warn("No parsed Excel document info found - falling back to regular save");
                                    // Save Excel file as a document
                                    RmaDocument excelDoc = uploadUtils.saveRmaDocument(rma, file);
                                    
                                    if (excelDoc != null) {
                                        if (rma.getDocuments() == null) {
                                            rma.setDocuments(new ArrayList<>());
                                            logger.info("Initialized documents collection for RMA");
                                        }
                                        rma.getDocuments().add(excelDoc);
                                        logger.info("Successfully saved Excel file as document (fallback)");
                                    } else {
                                        logger.warn("Failed to save Excel file as document");
                                    }
                                }
                                
                                // Skip adding to allFiles to prevent duplicate processing by RmaService
                                continue;
                            } else {
                                logger.info("Excel file not yet processed - saving as document");
                                // Save Excel file as a document
                                RmaDocument excelDoc = uploadUtils.saveRmaDocument(rma, file);
                                
                                if (excelDoc != null) {
                                    if (rma.getDocuments() == null) {
                                        rma.setDocuments(new ArrayList<>());
                                        logger.info("Initialized documents collection for RMA");
                                    }
                                    rma.getDocuments().add(excelDoc);
                                    logger.info("Successfully saved Excel file as document:");
                                    logger.info("  - File Name: {}", excelDoc.getFileName());
                                    logger.info("  - File Path: {}", excelDoc.getFilePath());
                                    logger.info("  - File Type: {}", excelDoc.getFileType());
                                    logger.info("  - File Size: {} bytes", excelDoc.getFileSize());
                                } else {
                                    logger.warn("Failed to save Excel file as document");
                                }
                            }
                            logger.info("=== EXCEL FILE PROCESSING COMPLETED ===");
                        }
                        
                        allFiles.add(file);
                    }
                }
            }
            
            // Handle any direct image uploads
            if (imageUploads != null) {
                for (MultipartFile file : imageUploads) {
                    if (file != null && !file.isEmpty()) {
                        logger.info("Processing image upload: {}, size: {}, type: {}", 
                            file.getOriginalFilename(), file.getSize(), file.getContentType());
                        allFiles.add(file);
                    }
                }
            }
            
            // Log the total combined file count
            logger.info("Combined total of {} files for upload", allFiles.size());
            
            // Filter out empty parts before saving
            if (rma.getPartLineItems() != null) {
                List<PartLineItem> originalParts = new ArrayList<>(rma.getPartLineItems());
                List<PartLineItem> filteredParts = new ArrayList<>();
                for (PartLineItem part : originalParts) {
                    // Only keep parts that have at least one meaningful field filled out
                    if ((part.getPartName() != null && !part.getPartName().trim().isEmpty()) ||
                        (part.getPartNumber() != null && !part.getPartNumber().trim().isEmpty()) ||
                        (part.getProductDescription() != null && !part.getProductDescription().trim().isEmpty())) {
                        filteredParts.add(part);
                        logger.info("Keeping part: name='{}', number='{}', desc='{}', qty={}", 
                            part.getPartName(), part.getPartNumber(), part.getProductDescription(), part.getQuantity());
                    } else {
                        logger.info("Filtering out empty part: name='{}', number='{}', desc='{}', qty={}", 
                            part.getPartName(), part.getPartNumber(), part.getProductDescription(), part.getQuantity());
                    }
                }
                rma.setPartLineItems(filteredParts);
                logger.info("Filtered parts: kept {} out of {} total parts", filteredParts.size(), originalParts.size());
            }
            
            // Convert back to array for the service method
            MultipartFile[] combinedUploads = allFiles.isEmpty() ? null : allFiles.toArray(new MultipartFile[0]);
            
            // Save the RMA with all files
            Rma savedRma = rmaService.saveRma(rma, combinedUploads);
            logger.info("RMA saved successfully with ID: {}", savedRma.getId());
            
            // Handle temporary moving parts data for new RMAs
            handleTempMovingParts(request, savedRma);
            
            // Log final document count
            int finalDocCount = savedRma.getDocuments() != null ? savedRma.getDocuments().size() : 0;
            logger.info("Final document count in saved RMA: {}", finalDocCount);
            if (savedRma.getDocuments() != null) {
                logger.info("Final document list:");
                for (RmaDocument doc : savedRma.getDocuments()) {
                    logger.info("  - {}", doc.getFileName());
                }
            }
            
            redirectAttributes.addFlashAttribute("message", "RMA saved successfully");
            return "redirect:/rma/" + savedRma.getId();
            
        } catch (Exception e) {
            logger.error("Error saving RMA: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error saving RMA: " + e.getMessage());
            return "redirect:/rma/list";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteRma(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        logger.info("DELETE REQUEST RECEIVED for RMA ID: {}", id);
        try {
            logger.info("Calling rmaService.deleteRma for ID: {}", id);
            rmaService.deleteRma(id);
            logger.info("RMA {} deleted successfully, redirecting to /rma", id);
            redirectAttributes.addFlashAttribute("message", "RMA deleted successfully.");
        } catch (Exception e) {
            logger.error("Error deleting RMA ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error deleting RMA: " + e.getMessage());
        }
        logger.info("Returning redirect:/rma");
        return "redirect:/rma";
    }

    // Re-add API endpoint for fetching tool details
    @GetMapping("/api/tool/{id}")
    @ResponseBody
    public Map<String, Object> ajaxGetToolDetails(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        Optional<Tool> toolOpt = toolService.getToolById(id);
        
        if (toolOpt.isPresent()) {
            Tool tool = toolOpt.get();
            result.put("id", tool.getId());
            result.put("name", tool.getName());
            result.put("secondaryName", tool.getSecondaryName());
            result.put("toolType", tool.getToolType());
            result.put("serialNumber1", tool.getSerialNumber1());
            result.put("serialNumber2", tool.getSerialNumber2());
            result.put("model1", tool.getModel1());
            result.put("model2", tool.getModel2());
            
            // Add new tool fields
            result.put("commissionDate", tool.getCommissionDate());
            result.put("chemicalGasService", tool.getChemicalGasService());
            result.put("startUpSl03Date", tool.getStartUpSl03Date());
            
            // Add GasGuard specific fields
            result.put("systemName", tool.getSystemName());
            result.put("equipmentLocation", tool.getEquipmentLocation());
            result.put("configNumber", tool.getConfigNumber());
            result.put("equipmentSet", tool.getEquipmentSet());
            
            if (tool.getLocationName() != null) {
                Map<String, Object> location = new HashMap<>();
                location.put("displayName", tool.getLocationName());
                result.put("location", location);
            }
        }
        
        return result;
    }

    // Add API endpoint for getting all tools
    @GetMapping("/api/tools")
    @ResponseBody
    public List<Map<String, Object>> getAllTools() {
        List<Tool> tools = toolService.getAllTools();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Tool tool : tools) {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("id", tool.getId());
            toolMap.put("name", tool.getName());
            toolMap.put("chemicalGasService", tool.getChemicalGasService());
            
            if (tool.getLocationName() != null) {
                toolMap.put("location", tool.getLocationName());
            }
            
            result.add(toolMap);
        }
        
        return result;
    }

    // Verify upload directory exists when accessing files
    @GetMapping("/files/**")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        try {
            // Get the file path from the request
            String requestURL = request.getRequestURL().toString();
            // Extract everything after "/files/"
            String filePath = requestURL.substring(requestURL.indexOf("/files/") + "/files/".length());
            
            logger.info("Attempting to serve file: {}", filePath);
            
            // If the file path contains an absolute path (like C:/), extract just the relative portion
            if (filePath.contains(":/")) {
                // Extract just the path after "uploads/"
                int uploadsIndex = filePath.lastIndexOf("uploads/");
                if (uploadsIndex != -1) {
                    filePath = filePath.substring(uploadsIndex + "uploads/".length());
                    logger.info("Extracted relative path: {}", filePath);
                } else {
                    logger.warn("Could not find 'uploads/' in file path: {}", filePath);
                    return ResponseEntity.badRequest().build();
                }
            }
            
            // Create the complete file path using the configured upload directory
            String fullPath = uploadUtils.getUploadDir() + File.separator + filePath;
            logger.info("Full file path: {}", fullPath);
            
            // Check if the file exists
            if (!uploadUtils.fileExists(fullPath)) {
                logger.warn("File not found: {}", fullPath);
                return ResponseEntity.notFound().build();
            }
            
            // Create URL resource from the file
            File file = new File(fullPath);
            Resource resource = new UrlResource(file.toURI());
            
            // Determine content type
            String contentType = null;
            try {
                contentType = Files.probeContentType(Paths.get(file.getAbsolutePath()));
            } catch (IOException e) {
                logger.warn("Could not determine content type for file: {}", filePath);
            }
            
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            logger.error("Error serving file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Get upload directory info
    @GetMapping("/api/upload-info")
    @ResponseBody
    public Map<String, String> getUploadInfo() {
        try {
            Map<String, String> info = new HashMap<>();
            String uploadDirectory = uploadUtils.getUploadDir();
            info.put("uploadDir", uploadDirectory);
            info.put("uploadDirectory", uploadDirectory);
            info.put("status", "OK");
            
            File dir = new File(uploadDirectory);
            if (dir.exists() && dir.isDirectory()) {
                info.put("exists", "true");
                info.put("canWrite", String.valueOf(dir.canWrite()));
                info.put("canRead", String.valueOf(dir.canRead()));
                info.put("freeSpace", String.valueOf(dir.getFreeSpace()));
                info.put("absolutePath", dir.getAbsolutePath());
            } else {
                info.put("exists", "false");
                info.put("error", "Upload directory does not exist");
                info.put("createAttempt", "true");
                
                // Try to create the directory
                boolean created = dir.mkdirs();
                info.put("created", String.valueOf(created));
                if (created) {
                    info.put("createdPath", dir.getAbsolutePath());
                }
            }
            
            return info;
        } catch (Exception e) {
            logger.error("Error getting upload info: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * Diagnostic API endpoint for RMA with complete file data
     */
    @GetMapping("/api/diagnose/{id}")
    @ResponseBody
    public Map<String, Object> diagnoseRma(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            
            if (rmaOpt.isPresent()) {
                Rma rma = rmaOpt.get();
                result.put("rmaId", rma.getId());
                result.put("rmaNumber", rma.getRmaNumber());
                
                // Force initialization of collections 
                if (rma.getPictures() != null) {
                    List<Map<String, Object>> pictureData = new ArrayList<>();
                    
                    for (RmaPicture pic : rma.getPictures()) {
                        Map<String, Object> picInfo = new HashMap<>();
                        picInfo.put("id", pic.getId());
                        picInfo.put("fileName", pic.getFileName());
                        picInfo.put("filePath", pic.getFilePath());
                        picInfo.put("fileType", pic.getFileType());
                        picInfo.put("fileSize", pic.getFileSize());
                        
                        // Check if file exists on disk
                        boolean fileExists = uploadUtils.fileExists(pic.getFilePath());
                        picInfo.put("existsOnDisk", fileExists);
                        
                        pictureData.add(picInfo);
                    }
                    
                    result.put("pictureCount", rma.getPictures().size());
                    result.put("pictures", pictureData);
                } else {
                    result.put("pictureCount", 0);
                    result.put("pictures", Collections.emptyList());
                }
                
                // Same for documents
                if (rma.getDocuments() != null) {
                    List<Map<String, Object>> docData = new ArrayList<>();
                    
                    for (RmaDocument doc : rma.getDocuments()) {
                        Map<String, Object> docInfo = new HashMap<>();
                        docInfo.put("id", doc.getId());
                        docInfo.put("fileName", doc.getFileName());
                        docInfo.put("filePath", doc.getFilePath());
                        docInfo.put("fileType", doc.getFileType());
                        docInfo.put("fileSize", doc.getFileSize());
                        
                        // Check if file exists on disk
                        boolean fileExists = uploadUtils.fileExists(doc.getFilePath());
                        docInfo.put("existsOnDisk", fileExists);
                        
                        docData.add(docInfo);
                    }
                    
                    result.put("documentCount", rma.getDocuments().size());
                    result.put("documents", docData);
                } else {
                    result.put("documentCount", 0);
                    result.put("documents", Collections.emptyList());
                }
                
                result.put("uploadDirConfig", uploadUtils.getUploadDir());
                result.put("success", true);
                
            } else {
                result.put("error", "RMA not found with ID: " + id);
                result.put("success", false);
            }
        } catch (Exception e) {
            logger.error("Error diagnosing RMA: ", e);
            result.put("error", "Exception: " + e.getMessage());
            result.put("success", false);
        }
        
        return result;
    }
    
    // Check if a file exists
    @GetMapping("/api/file-exists")
    @ResponseBody
    public Map<String, Object> checkFileExists(@RequestParam String path) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean exists = uploadUtils.fileExists(path);
            result.put("exists", exists);
            result.put("path", path);
            
            if (exists) {
                File file = new File(path);
                result.put("size", file.length());
                result.put("lastModified", file.lastModified());
                result.put("canRead", file.canRead());
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error checking if file exists: {}", e.getMessage(), e);
            result.put("exists", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
    
    /**
     * Handle file deletion requests
     */
    @PostMapping("/file/delete")
    public ResponseEntity<?> deleteFile(@RequestParam Long fileId,
                       @RequestParam String fileType,
                       @RequestParam Long rmaId) {
        logger.info("Deleting {} with ID {} from RMA {}", fileType, fileId, rmaId);
        
        try {
            boolean success = false;
            
            if ("picture".equalsIgnoreCase(fileType)) {
                success = rmaService.deletePicture(fileId);
            } else if ("document".equalsIgnoreCase(fileType)) {
                success = rmaService.deleteDocument(fileId);
            } else {
                logger.warn("Invalid file type: {}", fileType);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Invalid file type");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (!success) {
                logger.warn("Failed to delete {} with ID {}", fileType, fileId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Failed to delete file");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            logger.info("Successfully deleted {} with ID {}", fileType, fileId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting file: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * API endpoint to transfer a file between RMAs
     */
    @PostMapping("/file/transfer")
    public ResponseEntity<?> transferFile(@RequestParam Long fileId,
                       @RequestParam String fileType,
                       @RequestParam Long sourceRmaId,
                       @RequestParam Long targetRmaId) {
        logger.info("API request to transfer file - ID: {}, Type: {}, Source RMA: {}, Target RMA: {}", 
                  fileId, fileType, sourceRmaId, targetRmaId);
        
        try {
            boolean success;
            
            if ("document".equalsIgnoreCase(fileType)) {
                success = fileTransferService.transferDocument(fileId, targetRmaId);
            } else if ("picture".equalsIgnoreCase(fileType)) {
                success = fileTransferService.transferPicture(fileId, targetRmaId);
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid file type: " + fileType
                ));
            }
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "File transferred successfully"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to transfer file. Check logs for details."
                ));
            }
        } catch (Exception e) {
            logger.error("Error transferring file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * API endpoint to link a file between RMA and other entities (Tool, Passdown)
     */
    @PostMapping("/file/link")
    public String linkFile(@RequestParam Long fileId,
                           @RequestParam String fileType,
                           @RequestParam Long sourceRmaId,
                           @RequestParam String linkTarget,
                           @RequestParam(required = false) Long linkToolId,
                           @RequestParam(required = false) Long linkPassdownId) {
        
        logger.info("Request to link file - ID: {}, Type: {}, Source RMA: {}, Target Type: {}, Target ID: {}/{}",
                 fileId, fileType, sourceRmaId, linkTarget, linkToolId, linkPassdownId);
        
        try {
            boolean success = false;
            
            if ("tool".equals(linkTarget) && linkToolId != null) {
                // Handle linking to tool
                logger.info("Linking {} to Tool ID: {}", fileType, linkToolId);
                if ("document".equalsIgnoreCase(fileType)) {
                    // Logic for linking document to tool
                    success = rmaService.linkDocumentToTool(fileId, linkToolId);
                } else if ("picture".equalsIgnoreCase(fileType)) {
                    // Logic for linking picture to tool
                    success = rmaService.linkPictureToTool(fileId, linkToolId);
                }
            } else if ("passdown".equals(linkTarget) && linkPassdownId != null) {
                // Handle linking to passdown
                logger.info("Linking {} to Passdown ID: {}", fileType, linkPassdownId);
                if ("document".equalsIgnoreCase(fileType)) {
                    // Logic for linking document to passdown
                    success = rmaService.linkDocumentToPassdown(fileId, linkPassdownId);
                } else if ("picture".equalsIgnoreCase(fileType)) {
                    // Logic for linking picture to passdown
                    success = rmaService.linkPictureToPassdown(fileId, linkPassdownId);
                }
            }
            
            if (success) {
                logger.info("Successfully linked file");
            } else {
                logger.warn("Failed to link file");
            }
            
        } catch (Exception e) {
            logger.error("Error linking file: {}", e.getMessage(), e);
        }
        
        // Return to the RMA view
        return "redirect:/rma/" + sourceRmaId;
    }

    /**
     * Test endpoint to verify controller mapping
     */
    @GetMapping("/{id}/test-upload")
    @ResponseBody
    public String testUpload(@PathVariable Long id) {
        return "Test upload endpoint works for RMA ID: " + id;
    }

    /**
     * Upload pictures for RMA and redirect back to detail page
     */
    @PostMapping("/{id}/upload")
    public String uploadPictures(@PathVariable Long id,
                                @RequestParam("files") MultipartFile[] files,
                                RedirectAttributes redirectAttributes) {
        try {
            // Verify RMA exists
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "RMA not found");
                return "redirect:/rma";
            }
            
            // Get current user for upload tracking
            User currentUser = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
                String username = authentication.getName();
                currentUser = userService.getUserByUsername(username).orElse(null);
                if (currentUser == null) {
                    logger.warn("Could not find user with username: {}", username);
                }
            }
            
            Rma rma = rmaOpt.get();
            int successCount = 0;
            
            // Process each file
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                
                try {
                    // Validate file
                    if (!uploadUtils.validateFile(file)) {
                        logger.warn("File validation failed for: {}", file.getOriginalFilename());
                        continue;
                    }
                    
                    // Only process images
                    String contentType = file.getContentType();
                    if (contentType == null || !contentType.startsWith("image/")) {
                        logger.warn("File is not an image: {}", file.getOriginalFilename());
                        continue;
                    }
                    
                    // Use the new upload method with tracking
                    RmaPicture picture = uploadUtils.saveRmaPicture(rma, file, currentUser);
                    if (picture != null) {
                        rma.getPictures().add(picture);
                        successCount++;
                    } else {
                        logger.warn("Failed to save picture: {}", file.getOriginalFilename());
                    }
                    
                } catch (Exception e) {
                    logger.error("Error processing picture {}: {}", file.getOriginalFilename(), e.getMessage());
                }
            }
            
            // Save RMA with new pictures
            if (successCount > 0) {
                rmaService.saveRma(rma, new MultipartFile[0]);
                redirectAttributes.addFlashAttribute("success", 
                    successCount + " picture(s) uploaded successfully");
            } else {
                redirectAttributes.addFlashAttribute("error", "No pictures were uploaded");
            }
            
        } catch (Exception e) {
            logger.error("Error uploading pictures for RMA {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error uploading pictures: " + e.getMessage());
        }
        
        return "redirect:/rma/" + id;
    }
    
    /**
     * Upload documents for RMA and redirect back to detail page
     */
    @PostMapping("/{id}/documents/upload")
    public String uploadDocuments(@PathVariable Long id,
                                 @RequestParam("files") MultipartFile[] files,
                                 RedirectAttributes redirectAttributes) {
        try {
            // Verify RMA exists
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "RMA not found");
                return "redirect:/rma";
            }
            
            Rma rma = rmaOpt.get();
            int successCount = 0;
            
            // Process each file
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                
                try {
                    // Validate file
                    if (!uploadUtils.validateFile(file)) {
                        logger.warn("File validation failed for: {}", file.getOriginalFilename());
                        continue;
                    }
                    
                    // Save file to disk
                    String filePath = uploadUtils.saveFile(file, "rma-documents");
                    if (filePath == null) {
                        logger.warn("Failed to save file: {}", file.getOriginalFilename());
                        continue;
                    }
                    
                    // Create document entity
                    RmaDocument document = new RmaDocument();
                    document.setFileName(file.getOriginalFilename());
                    document.setFilePath(filePath);
                    document.setFileType(file.getContentType());
                    document.setFileSize(file.getSize());
                    document.setRma(rma);
                    
                    rma.getDocuments().add(document);
                    successCount++;
                    
                } catch (Exception e) {
                    logger.error("Error processing document {}: {}", file.getOriginalFilename(), e.getMessage());
                }
            }
            
            // Save RMA with new documents
            if (successCount > 0) {
                rmaService.saveRma(rma, new MultipartFile[0]);
                redirectAttributes.addFlashAttribute("success", 
                    successCount + " document(s) uploaded successfully");
            } else {
                redirectAttributes.addFlashAttribute("error", "No documents were uploaded");
            }
            
        } catch (Exception e) {
            logger.error("Error uploading documents for RMA {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error uploading documents: " + e.getMessage());
        }
        
        return "redirect:/rma/" + id;
    }

    /**
     * Generate Excel RMA form
     */
    @GetMapping("/{id}/excel")
    public ResponseEntity<byte[]> generateExcelRma(@PathVariable Long id, Principal principal) {
        try {
            logger.info("===== GENERATING EXCEL RMA ID: {} =====", id);
            
            // Get the RMA
            Rma rma = rmaService.getRmaById(id)
                    .orElseThrow(() -> new RuntimeException("RMA not found with ID: " + id));
            
            // Debug - print all RMA fields to help diagnose the issue
            logger.info("RMA DETAILS - ID: {}, Number: '{}', SAP Notification: '{}', Service Order: '{}'", 
                       id, rma.getRmaNumber(), rma.getSapNotificationNumber(), rma.getServiceOrder());
            
            logger.info("TOOL INFO - Tool: {}", rma.getTool() != null ? rma.getTool().getName() : "null");
            
            // Debug - examine part line items in detail
            if (rma.getPartLineItems() != null) {
                logger.info("PART LINE ITEMS COUNT: {}", rma.getPartLineItems().size());
                for (int i = 0; i < rma.getPartLineItems().size(); i++) {
                    PartLineItem part = rma.getPartLineItems().get(i);
                    logger.info("PART #{} - Name: '{}', Number: '{}', Desc: '{}'", 
                              i+1, 
                              part.getPartName(), 
                              part.getPartNumber(), 
                              part.getProductDescription());
                }
            } else {
                logger.info("PART LINE ITEMS: null");
            }
            
            // Get current user
            User currentUser = null;
            if (principal != null) {
                currentUser = userService.getUserByEmail(principal.getName())
                        .orElse(null);
            }
            
            // Generate the Excel file
            byte[] excelContent = excelService.populateRmaTemplate(rma, currentUser);
            
            // Build descriptive filename
            StringBuilder filenameBuilder = new StringBuilder();
            logger.info("Building filename for RMA export. RMA Number: '{}', SAP Notification: '{}', ID: {}", 
                        rma.getRmaNumber(), rma.getSapNotificationNumber(), id);

            // Start with "RMA" as a prefix to always ensure context
            filenameBuilder.append("RMA");
            
            // Add RMA number, SAP notification, or ID
            if (rma.getRmaNumber() != null && !rma.getRmaNumber().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getRmaNumber().trim());
                logger.info("Added RMA number to filename: '{}'", rma.getRmaNumber().trim());
            } else if (rma.getSapNotificationNumber() != null && !rma.getSapNotificationNumber().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getSapNotificationNumber().trim());
                logger.info("Added SAP notification number to filename: '{}'", rma.getSapNotificationNumber().trim());
            } else if (rma.getServiceOrder() != null && !rma.getServiceOrder().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getServiceOrder().trim());
                logger.info("Added Service Order to filename: '{}'", rma.getServiceOrder().trim());
            } else {
                filenameBuilder.append("_").append(id);
                logger.info("Added ID to filename: {}", id);
            }

            // Add tool name if available
            if (rma.getTool() != null && rma.getTool().getName() != null && !rma.getTool().getName().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getTool().getName().trim());
                logger.info("Added tool name to filename: '{}'", rma.getTool().getName().trim());
            }

            // Add first part name/number if available
            boolean partInfoAdded = false;
            if (rma.getPartLineItems() != null && !rma.getPartLineItems().isEmpty()) {
                logger.info("Found {} part line items to consider for filename", rma.getPartLineItems().size());
                for (PartLineItem part : rma.getPartLineItems()) {
                    if (part.getPartNumber() != null && !part.getPartNumber().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getPartNumber().trim());
                        logger.info("Added part number to filename: '{}'", part.getPartNumber().trim());
                        partInfoAdded = true;
                        break;
                    } else if (part.getPartName() != null && !part.getPartName().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getPartName().trim());
                        logger.info("Added part name to filename: '{}'", part.getPartName().trim());
                        partInfoAdded = true;
                        break;
                    } else if (part.getProductDescription() != null && !part.getProductDescription().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getProductDescription().trim());
                        logger.info("Added product description to filename: '{}'", part.getProductDescription().trim());
                        partInfoAdded = true;
                        break;
                    }
                }
                if (!partInfoAdded) {
                    logger.warn("No valid part numbers or names found in the part line items");
                }
            } else {
                logger.info("No part line items available to add to filename");
            }

            // Always add a timestamp for uniqueness
            filenameBuilder.append("_").append(System.currentTimeMillis());
            logger.info("Added timestamp to ensure unique filename");

            // Clean filename and ensure it ends with .xlsx
            String filename = filenameBuilder.toString()
                    .replaceAll("[\\\\/:*?\"<>|]", "_") // Replace invalid filename chars
                    .replaceAll("\\s+", "_") // Replace spaces with underscores
                    .trim() + ".xlsx";

            logger.info("Final Excel export filename: '{}'", filename);
            
            // Set up response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            return new ResponseEntity<>(excelContent, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating Excel RMA form", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles Excel file upload to pre-populate RMA form
     */
    @PostMapping("/uploadExcel")
    @ResponseBody
    public Map<String, Object> uploadExcelFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("error", "Uploaded file is empty");
                return response;
            }
            
            // Check file extension 
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !(originalFilename.endsWith(".xlsx") || originalFilename.endsWith(".xls"))) {
                response.put("success", false);
                response.put("error", "Uploaded file is not an Excel file");
                return response;
            }
            
            // Extract data from Excel
            byte[] fileBytes = file.getBytes();
            Map<String, Object> extractedData = excelService.extractRmaDataFromExcel(fileBytes);
            
            // Check if extraction was successful
            if (extractedData.containsKey("error")) {
                response.put("success", false);
                response.put("error", extractedData.get("error"));
                return response;
            }
            
            response.put("success", true);
            response.put("data", extractedData);
            
        } catch (Exception e) {
            logger.error("Error uploading Excel file", e);
            response.put("success", false);
            response.put("error", "Failed to process Excel file: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * Handle export of draft (unsaved) RMA to Excel
     */
    @PostMapping("/export-draft")
    public ResponseEntity<byte[]> exportDraftRma(@ModelAttribute Rma rma, Principal principal) {
        try {
            // Add more detailed logging for debugging
            logger.info("===== EXPORTING DRAFT/UNSAVED RMA WITH FORM VALUES =====");
            logger.info("Exporting data for RMA ID: {}, Number: '{}', SAP: '{}'", 
                       rma.getId(), rma.getRmaNumber(), rma.getSapNotificationNumber());
            
            // Report on parts data
            if (rma.getPartLineItems() != null) {
                logger.info("Found {} part line items in form data", rma.getPartLineItems().size());
                int index = 0;
                for (PartLineItem part : rma.getPartLineItems()) {
                    logger.info("Part #{}: Name='{}', Number: '{}', Desc='{}'", 
                               ++index, part.getPartName(), part.getPartNumber(), part.getProductDescription());
                }
            } else {
                logger.info("No part line items found in form data");
            }
            
            // Get current user
            User currentUser = null;
            if (principal != null) {
                currentUser = userService.getUserByEmail(principal.getName())
                        .orElse(null);
            }
            
            // Generate the Excel file from the draft RMA
            byte[] excelContent = excelService.populateRmaTemplate(rma, currentUser);
            
            // Build descriptive filename
            StringBuilder filenameBuilder = new StringBuilder();
            logger.info("Building filename for draft RMA export. RMA Number: '{}', SAP Notification: '{}'", 
                       rma.getRmaNumber(), rma.getSapNotificationNumber());

            // Start with "RMA" as a prefix to always ensure context
            filenameBuilder.append("RMA");
            
            // Add RMA number, SAP notification, Service Order or Draft
            if (rma.getRmaNumber() != null && !rma.getRmaNumber().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getRmaNumber().trim());
                logger.info("Added RMA number to filename: '{}'", rma.getRmaNumber().trim());
            } else if (rma.getSapNotificationNumber() != null && !rma.getSapNotificationNumber().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getSapNotificationNumber().trim());
                logger.info("Added SAP notification number to filename: '{}'", rma.getSapNotificationNumber().trim());
            } else if (rma.getServiceOrder() != null && !rma.getServiceOrder().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getServiceOrder().trim());
                logger.info("Added Service Order to filename: '{}'", rma.getServiceOrder().trim());
            } else {
                filenameBuilder.append("_Draft");
                logger.info("Added 'Draft' to filename");
            }

            // Add tool name if available
            if (rma.getTool() != null && rma.getTool().getName() != null && !rma.getTool().getName().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getTool().getName().trim());
                logger.info("Added tool name to filename: '{}'", rma.getTool().getName().trim());
            } else {
                logger.info("No tool name available to add to filename");
            }

            // Add first part name/number if available
            boolean partInfoAdded = false;
            if (rma.getPartLineItems() != null && !rma.getPartLineItems().isEmpty()) {
                logger.info("Found {} part line items to consider for filename", rma.getPartLineItems().size());
                for (PartLineItem part : rma.getPartLineItems()) {
                    if (part.getPartNumber() != null && !part.getPartNumber().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getPartNumber().trim());
                        logger.info("Added part number to filename: '{}'", part.getPartNumber().trim());
                        partInfoAdded = true;
                        break;
                    } else if (part.getPartName() != null && !part.getPartName().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getPartName().trim());
                        logger.info("Added part name to filename: '{}'", part.getPartName().trim());
                        partInfoAdded = true;
                        break;
                    } else if (part.getProductDescription() != null && !part.getProductDescription().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getProductDescription().trim());
                        logger.info("Added product description to filename: '{}'", part.getProductDescription().trim());
                        partInfoAdded = true;
                        break;
                    }
                }
                if (!partInfoAdded) {
                    logger.warn("No valid part numbers or names found in the part line items");
                }
            } else {
                logger.info("No part line items available to add to filename");
            }

            // Always add a timestamp for uniqueness
            filenameBuilder.append("_").append(System.currentTimeMillis());
            logger.info("Added timestamp to ensure unique filename");

            // Clean filename and ensure it ends with .xlsx
            String filename = filenameBuilder.toString()
                    .replaceAll("[\\\\/:*?\"<>|]", "_") // Replace invalid filename chars
                    .replaceAll("\\s+", "_") // Replace spaces with underscores
                    .trim() + ".xlsx";

            logger.info("Final Excel export filename for form data: '{}'", filename);
            
            // Set up response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            return new ResponseEntity<>(excelContent, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating Excel from form data", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles adding a new comment to an RMA
     */
    @GetMapping("/{id}/post-comment")
    public String getPostComment(@PathVariable Long id) {
        return "rma/post-comment";
    }

    @PostMapping("/{id}/post-comment")
    public String postComment(@PathVariable Long id, 
                            @RequestParam("content") String content,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        logger.info("=== POST COMMENT ENDPOINT HIT ===");
        logger.info("RMA ID: {}", id);
        logger.info("Content: '{}'", content);
        logger.info("Authentication: {}", authentication != null ? authentication.getName() : "NULL");
        
        try {
            String userEmail = authentication.getName();
            logger.info("Calling rmaService.addComment...");
            rmaService.addComment(id, content, userEmail);
            logger.info("Comment added successfully!");
            redirectAttributes.addFlashAttribute("message", "Comment added successfully");
        } catch (Exception e) {
            logger.error("Error adding comment to RMA {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error adding comment: " + e.getMessage());
        }
        return "redirect:/rma/" + id;
    }

    @PostMapping("/{id}/update-notes")
    @ResponseBody
    public Map<String, Object> updateNotes(@PathVariable Long id, HttpServletRequest request) {
        logger.info("Updating notes for RMA ID: {}", id);
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            String notes = request.getParameter("notes");
            rma.setNotes(notes);
            
            rmaService.saveRma(rma, null);
            response.put("success", true);
            response.put("message", "Notes updated successfully");
            
        } catch (Exception e) {
            logger.error("Error updating notes for RMA {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error updating notes: " + e.getMessage());
        }
        
        return response;
    }
    
    @PostMapping("/{id}/update-status")
    @ResponseBody
    public Map<String, Object> updateStatus(@PathVariable Long id, HttpServletRequest request) {
        logger.info("Updating status for RMA ID: {}", id);
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            String statusParam = request.getParameter("newStatus");
            
            if (statusParam != null && !statusParam.trim().isEmpty()) {
                try {
                    RmaStatus newStatus = RmaStatus.valueOf(statusParam);
                    rma.setStatus(newStatus);
                    
                    rmaService.saveRma(rma, null);
                    response.put("success", true);
                    response.put("message", "Status updated successfully");
                    response.put("newStatusDisplay", newStatus.getDisplayName());
                    response.put("newStatus", newStatus.name());
                    
                } catch (IllegalArgumentException e) {
                    response.put("success", false);
                    response.put("message", "Invalid status value: " + statusParam);
                }
            } else {
                response.put("success", false);
                response.put("message", "Status parameter is required");
            }
            
        } catch (Exception e) {
            logger.error("Error updating status for RMA {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error updating status: " + e.getMessage());
        }
        
        return response;
    }
    
    @PostMapping("/{id}/update-priority")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updatePriority(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        String priorityValue = request.getParameter("newPriority");
        logger.info("Received request to update priority for RMA ID {} to {}", id, priorityValue);

        if (!StringUtils.hasText(priorityValue)) {
            response.put("success", false);
            response.put("message", "New priority value cannot be empty.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            RmaPriority newPriority = RmaPriority.valueOf(priorityValue);
            rmaService.updateRmaPriority(id, newPriority);
            response.put("success", true);
            logger.info("Updated priority for RMA ID {} to {}. Display: {}", id, newPriority, newPriority.getDisplayName());
            response.put("newPriorityDisplay", newPriority.getDisplayName());
            response.put("newPriority", newPriority.name()); // Send enum name for JS to map to badge class
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid priority value received: {}", priorityValue, e);
            response.put("success", false);
            response.put("message", "Invalid priority value.");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error updating priority for RMA ID {}", id, e);
            response.put("success", false);
            response.put("message", "Error updating priority: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }
    
    private String getStatusBadgeClass(RmaStatus status) {
        return switch (status) {
            case RMA_WRITTEN_EMAILED -> "badge rounded-pill fs-6 bg-primary";
            case NUMBER_PROVIDED -> "badge rounded-pill fs-6 bg-info";
            case MEMO_EMAILED -> "badge rounded-pill fs-6 bg-secondary";
            case RECEIVED_PARTS -> "badge rounded-pill fs-6 bg-warning";
            case WAITING_CUSTOMER, WAITING_FSE -> "badge rounded-pill fs-6 bg-danger";
            case COMPLETED -> "badge rounded-pill fs-6 bg-success";
            default -> "badge rounded-pill fs-6 bg-light";
        };
    }

    /**
     * Add moving part from RMA context
     */
    @PostMapping("/{id}/moving-parts/add")
    public String addMovingPartFromRma(@PathVariable("id") Long rmaId,
                                     @RequestParam("partName") String partName,
                                     @RequestParam(value = "fromToolId", required = false) Long fromToolId,
                                     @RequestParam(value = "fromCustomLocation", required = false) String fromCustomLocation,
                                     @RequestParam(value = "destinationToolIds", required = false) List<Long> destinationToolIds,
                                     @RequestParam(value = "toCustomLocations", required = false) List<String> toCustomLocations,
                                     @RequestParam(value = "notes", required = false) String notes,
                                     RedirectAttributes redirectAttributes) {
        
        try {
            // Get the RMA to link the moving part to
            Optional<Rma> rmaOpt = rmaService.getRmaById(rmaId);
            if (rmaOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "RMA not found");
                return "redirect:/rma";
            }
            
            Rma rma = rmaOpt.get();
            
            // Note: Validation is handled by JavaScript on the frontend
            
            MovingPart movingPart = movingPartService.createMovingPart(partName, fromToolId, fromCustomLocation, 
                                                                       destinationToolIds, toCustomLocations, 
                                                                       notes, null, rma);
            
            redirectAttributes.addFlashAttribute("message", "Moving part recorded successfully");
        } catch (Exception e) {
            logger.error("Error adding moving part from RMA {}: {}", rmaId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error recording moving part: " + e.getMessage());
        }
        
        return "redirect:/rma/" + rmaId;
    }

    /**
     * Edit moving part from RMA context
     */
    @PostMapping("/{rmaId}/moving-parts/{movingPartId}/edit")
    public String editMovingPartFromRma(@PathVariable("rmaId") Long rmaId,
                                       @PathVariable("movingPartId") Long movingPartId,
                                       @RequestParam("partName") String partName,
                                       @RequestParam("fromToolId") Long fromToolId,
                                       @RequestParam("destinationToolIds") List<Long> destinationToolIds,
                                       @RequestParam(value = "notes", required = false) String notes,
                                       RedirectAttributes redirectAttributes) {
        
        try {
            // Get the existing moving part
            Optional<MovingPart> movingPartOpt = movingPartService.getMovingPartById(movingPartId);
            if (movingPartOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Moving part not found");
                return "redirect:/rma/" + rmaId;
            }
            
            MovingPart movingPart = movingPartOpt.get();
            
            // Use the service method to update the moving part
            Optional<MovingPart> result = movingPartService.updateMovingPart(movingPartId, partName, fromToolId, destinationToolIds, notes, null);
            
            if (result.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Failed to update moving part");
                return "redirect:/rma/" + rmaId;
            }
            
            redirectAttributes.addFlashAttribute("success", "Moving part updated successfully");
            
        } catch (Exception e) {
            logger.error("Error updating moving part from RMA context", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update moving part: " + e.getMessage());
        }
        
        return "redirect:/rma/" + rmaId;
    }
    
    /**
     * Delete moving part from RMA context
     */
    @PostMapping("/{rmaId}/moving-parts/{movingPartId}/delete")
    public String deleteMovingPartFromRma(@PathVariable("rmaId") Long rmaId,
                                         @PathVariable("movingPartId") Long movingPartId,
                                         RedirectAttributes redirectAttributes) {
        
        try {
            // Get the existing moving part
            Optional<MovingPart> movingPartOpt = movingPartService.getMovingPartById(movingPartId);
            if (movingPartOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Moving part not found");
                return "redirect:/rma/" + rmaId;
            }
            
            // Delete the moving part
            movingPartService.deleteMovingPart(movingPartId);
            
            redirectAttributes.addFlashAttribute("success", "Moving part deleted successfully");
            
        } catch (Exception e) {
            logger.error("Error deleting moving part from RMA context", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete moving part: " + e.getMessage());
        }
        
        return "redirect:/rma/" + rmaId;
    }

    @PostMapping("/{id}/update-header")
    @ResponseBody
    public Map<String, Object> updateHeader(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            
            // Get updated values from request
            String referenceNumber = request.getParameter("referenceNumber");
            String rmaNumber = request.getParameter("rmaNumber");
            String sapNotificationNumber = request.getParameter("sapNotificationNumber");
            String serviceOrder = request.getParameter("serviceOrder");
            String reasonForRequest = request.getParameter("reasonForRequest");
            String dssProductLine = request.getParameter("dssProductLine");
            String systemDescription = request.getParameter("systemDescription");
            String toolIdStr = request.getParameter("toolId");
            String locationIdStr = request.getParameter("locationId"); 
            String technicianName = request.getParameter("technician"); 

            // Update reference number (prioritize the new consolidated field)
            if (referenceNumber != null) {
                rma.setReferenceNumber(referenceNumber.trim().isEmpty() ? null : referenceNumber.trim());
            } else {
                // Fallback to old separate fields for backward compatibility
                if (rmaNumber != null) {
                    rma.setRmaNumber(rmaNumber.trim().isEmpty() ? null : rmaNumber.trim());
                }
                if (sapNotificationNumber != null) {
                    rma.setSapNotificationNumber(sapNotificationNumber.trim().isEmpty() ? null : sapNotificationNumber.trim());
                }
            }
            
            // Update service order
            if (serviceOrder != null) {
                rma.setServiceOrder(serviceOrder.trim().isEmpty() ? null : serviceOrder.trim());
            }
            
            // Update reason for request
            if (reasonForRequest != null && !reasonForRequest.trim().isEmpty()) {
                try {
                    rma.setReasonForRequest(RmaReasonForRequest.valueOf(reasonForRequest.trim()));
                } catch (IllegalArgumentException e) {
                    rma.setReasonForRequest(null);
                }
            } else {
                rma.setReasonForRequest(null);
            }
            
            // Update DSS product line
            if (dssProductLine != null && !dssProductLine.trim().isEmpty()) {
                try {
                    rma.setDssProductLine(DssProductLine.valueOf(dssProductLine.trim()));
                } catch (IllegalArgumentException e) {
                    rma.setDssProductLine(null);
                }
            } else {
                rma.setDssProductLine(null);
            }
            
            // Update system description
            if (systemDescription != null && !systemDescription.trim().isEmpty()) {
                try {
                    rma.setSystemDescription(SystemDescription.valueOf(systemDescription.trim()));
                } catch (IllegalArgumentException e) {
                    rma.setSystemDescription(null);
                }
            } else {
                rma.setSystemDescription(null);
            }
            
            // Update tool
            if (toolIdStr != null && !toolIdStr.trim().isEmpty()) {
                try {
                    Long toolId = Long.parseLong(toolIdStr);
                    Optional<Tool> toolOpt = toolService.getToolById(toolId);
                    if (toolOpt.isPresent()) {
                        rma.setTool(toolOpt.get());
                    } else {
                        response.put("success", false);
                        response.put("message", "Selected tool not found");
                        return response;
                    }
                } catch (NumberFormatException e) {
                    response.put("success", false);
                    response.put("message", "Invalid tool ID format");
                    return response;
                }
            } else {
                // Allow setting tool to null (no tool selected)
                rma.setTool(null);
            }
            
            // Update Location
            if (locationIdStr != null && !locationIdStr.trim().isEmpty()) {
                try {
                    Long locationId = Long.parseLong(locationIdStr);
                    Optional<Location> locationOpt = locationService.getLocationById(locationId);
                    if (locationOpt.isPresent()) {
                        rma.setLocation(locationOpt.get());
                    } else {
                        response.put("success", false);
                        response.put("message", "Selected location not found");
                        return response;
                    }
                } catch (NumberFormatException e) {
                    response.put("success", false);
                    response.put("message", "Invalid location ID format");
                    return response;
                }
            } else {
                rma.setLocation(null); 
            }

            // Update Technician
            if (technicianName != null) {
                rma.setTechnician(technicianName.trim().isEmpty() ? null : technicianName.trim());
            }

            // Save the updated RMA
            rmaService.saveRma(rma, null);
            
            response.put("success", true);
            response.put("message", "Header information updated successfully");
            response.put("rmaNumber", rma.getRmaNumber()); 
            response.put("sapNotificationNumber", rma.getSapNotificationNumber()); 
            
            if (rma.getTool() != null) {
                Tool tool = rma.getTool();
                response.put("toolId", tool.getId());
                response.put("toolName", tool.getName());
                response.put("toolSecondaryName", tool.getSecondaryName());
                response.put("toolTypeDisplay", tool.getToolType() != null ? tool.getToolType().getDisplayName() : "N/A");
                response.put("toolModel1", tool.getModel1());
                response.put("toolModel2", tool.getModel2());
                response.put("toolSerialNumber1", tool.getSerialNumber1());
                response.put("toolSerialNumber2", tool.getSerialNumber2());
                response.put("toolChemicalGasService", tool.getChemicalGasService());
            } else {
                response.put("toolId", "");
                response.put("toolName", "No Tool selected");
                response.put("toolSecondaryName", null);
                response.put("toolTypeDisplay", "N/A");
                response.put("toolModel1", null);
                response.put("toolModel2", null);
                response.put("toolSerialNumber1", null);
                response.put("toolSerialNumber2", null);
                response.put("toolChemicalGasService", null);
            }
            
            // Add response data for Basic Information fields
            response.put("serviceOrder", rma.getServiceOrder());
            response.put("reasonForRequestDisplay", rma.getReasonForRequest() != null ? rma.getReasonForRequest().getDisplayName() : null);
            response.put("dssProductLineDisplay", rma.getDssProductLine() != null ? rma.getDssProductLine().getDisplayName() : null);
            response.put("systemDescriptionDisplay", rma.getSystemDescription() != null ? rma.getSystemDescription().getDisplayName() : null);
            response.put("locationName", rma.getLocation() != null ? rma.getLocation().getDisplayName() : "N/A"); 
            response.put("technicianName", rma.getTechnician() != null ? rma.getTechnician() : "Not assigned"); 
            
            logger.info("Updated header for RMA ID {}: Reference Number: {}, Tool: {}, Location: {}, Technician: {}", 
                       id, rma.getReferenceNumber(), 
                       rma.getTool() != null ? rma.getTool().getName() : "None",
                       rma.getLocation() != null ? rma.getLocation().getDisplayName() : "None", 
                       rma.getTechnician() != null ? rma.getTechnician() : "None"); 
            
        } catch (Exception e) {
            logger.error("Error updating header for RMA ID " + id, e);
            response.put("success", false);
            response.put("message", "Error updating header: " + e.getMessage());
        }
        
        return response;
    }

    @PostMapping("/{id}/update-parts")
    @ResponseBody
    @Transactional
    public Map<String, Object> updateParts(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("=== UPDATE PARTS REQUEST for RMA ID: {} ===", id);
            
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            
            // Clear existing part line items
            rma.getPartLineItems().clear();
            logger.info("Cleared existing parts");
            
            // Process parts array from form
            int partIndex = 0;
            int addedParts = 0;
            
            while (true) {
                String partName = request.getParameter("parts[" + partIndex + "][partName]");
                String partNumber = request.getParameter("parts[" + partIndex + "][partNumber]");
                String productDescription = request.getParameter("parts[" + partIndex + "][productDescription]");
                String quantityStr = request.getParameter("parts[" + partIndex + "][quantity]");
                String replacementRequiredStr = request.getParameter("parts[" + partIndex + "][replacementRequired]");
                
                // If no part name parameter exists, we've reached the end
                if (partName == null) {
                    break;
                }
                
                // Only create part if at least one field has data
                if ((partName != null && !partName.trim().isEmpty()) ||
                    (partNumber != null && !partNumber.trim().isEmpty()) ||
                    (productDescription != null && !productDescription.trim().isEmpty())) {
                    
                    PartLineItem part = new PartLineItem();
                    part.setPartName(partName != null && !partName.trim().isEmpty() ? partName.trim() : null);
                    part.setPartNumber(partNumber != null && !partNumber.trim().isEmpty() ? partNumber.trim() : null);
                    part.setProductDescription(productDescription != null && !productDescription.trim().isEmpty() ? productDescription.trim() : null);
                    
                    // Parse quantity
                    int quantity = 1;
                    if (quantityStr != null && !quantityStr.trim().isEmpty()) {
                        try {
                            quantity = Integer.parseInt(quantityStr.trim());
                            if (quantity < 1) quantity = 1;
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid quantity '{}' for part {}, defaulting to 1", quantityStr, partIndex);
                        }
                    }
                    part.setQuantity(quantity);
                    
                    // Parse replacement required
                    boolean replacementRequired = "true".equals(replacementRequiredStr);
                    part.setReplacementRequired(replacementRequired);
                    
                    rma.getPartLineItems().add(part);
                    addedParts++;
                    
                    logger.info("Added part {}: name='{}', number='{}', desc='{}', qty={}, replacement={}", 
                        partIndex, part.getPartName(), part.getPartNumber(), 
                        part.getProductDescription(), part.getQuantity(), part.getReplacementRequired());
                }
                
                partIndex++;
            }
            
            logger.info("Processed {} parts, added {} to RMA", partIndex, addedParts);
            
            // Save the RMA
            Rma savedRma = rmaService.saveRma(rma, null);
            
            // Verify the save by reloading from database
            Optional<Rma> verifyRmaOpt = rmaService.getRmaById(id);
            if (verifyRmaOpt.isPresent()) {
                Rma verifyRma = verifyRmaOpt.get();
                logger.info("VERIFICATION: Reloaded RMA has {} parts after save", verifyRma.getPartLineItems().size());
                for (int i = 0; i < verifyRma.getPartLineItems().size(); i++) {
                    PartLineItem verifyItem = verifyRma.getPartLineItems().get(i);
                    logger.info("  Verified Part {}: name='{}', number='{}', qty={}", 
                        i, verifyItem.getPartName(), verifyItem.getPartNumber(), verifyItem.getQuantity());
                }
            }
            
            response.put("success", true);
            response.put("message", "Parts information updated successfully");
            response.put("addedCount", addedParts);
            
        } catch (Exception e) {
            logger.error("Error updating parts for RMA ID " + id, e);
            response.put("success", false);
            response.put("message", "Error updating parts: " + e.getMessage());
        }
        
        return response;
    }
    
    @PostMapping("/{id}/update-dates")
    @ResponseBody
    public Map<String, Object> updateDates(@PathVariable Long id, HttpServletRequest request) {
        logger.info("=== UPDATE DATES REQUEST ===");
        logger.info("RMA ID: {}", id);
        
        // Log all parameters
        request.getParameterNames().asIterator().forEachRemaining(name -> {
            logger.info("Parameter: {} = {}", name, request.getParameter(name));
        });
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            
            // Parse and update date fields
            String[] dateFields = {
                "writtenDate", "rmaNumberProvidedDate", "shippingMemoEmailedDate",
                "partsReceivedDate", "installedPartsDate", "failedPartsPackedDate", "failedPartsShippedDate"
            };
            
            for (String fieldName : dateFields) {
                String dateValue = request.getParameter(fieldName);
                logger.info("Processing field: {} with value: '{}'", fieldName, dateValue);
                if (dateValue != null && !dateValue.trim().isEmpty()) {
                    try {
                        LocalDate date = LocalDate.parse(dateValue);
                        logger.info("Parsed date for {}: {}", fieldName, date);
                        switch (fieldName) {
                            case "writtenDate":
                                rma.setWrittenDate(date);
                                break;
                            case "rmaNumberProvidedDate":
                                rma.setRmaNumberProvidedDate(date);
                                break;
                            case "shippingMemoEmailedDate":
                                rma.setShippingMemoEmailedDate(date);
                                break;
                            case "partsReceivedDate":
                                rma.setPartsReceivedDate(date);
                                break;
                            case "installedPartsDate":
                                rma.setInstalledPartsDate(date);
                                break;
                            case "failedPartsPackedDate":
                                rma.setFailedPartsPackedDate(date);
                                break;
                            case "failedPartsShippedDate":
                                rma.setFailedPartsShippedDate(date);
                                break;
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing date field {}: {}", fieldName, dateValue, e);
                    }
                } else {
                    // Set to null if empty
                    switch (fieldName) {
                        case "writtenDate":
                            rma.setWrittenDate(null);
                            break;
                        case "rmaNumberProvidedDate":
                            rma.setRmaNumberProvidedDate(null);
                            break;
                        case "shippingMemoEmailedDate":
                            rma.setShippingMemoEmailedDate(null);
                            break;
                        case "partsReceivedDate":
                            rma.setPartsReceivedDate(null);
                            break;
                        case "installedPartsDate":
                            rma.setInstalledPartsDate(null);
                            break;
                        case "failedPartsPackedDate":
                            rma.setFailedPartsPackedDate(null);
                            break;
                        case "failedPartsShippedDate":
                            rma.setFailedPartsShippedDate(null);
                            break;
                    }
                }
            }
            
            // Save the updated RMA
            logger.info("Saving updated RMA with ID: {}", id);
            Rma savedRma = rmaService.saveRma(rma, null);
            logger.info("RMA saved successfully. Current dates:");
            logger.info("  - writtenDate: {}", savedRma.getWrittenDate());
            logger.info("  - rmaNumberProvidedDate: {}", savedRma.getRmaNumberProvidedDate());
            logger.info("  - shippingMemoEmailedDate: {}", savedRma.getShippingMemoEmailedDate());
            logger.info("  - partsReceivedDate: {}", savedRma.getPartsReceivedDate());
            logger.info("  - installedPartsDate: {}", savedRma.getInstalledPartsDate());
            logger.info("  - failedPartsPackedDate: {}", savedRma.getFailedPartsPackedDate());
            logger.info("  - failedPartsShippedDate: {}", savedRma.getFailedPartsShippedDate());
            
            response.put("success", true);
            response.put("message", "Dates updated successfully");
            
            logger.info("Updated dates for RMA ID {}", id);
            
        } catch (Exception e) {
            logger.error("Error updating dates for RMA ID " + id, e);
            response.put("success", false);
            response.put("message", "Error updating dates: " + e.getMessage());
        }
        
        return response;
    }
    
    @PostMapping("/{id}/update-problem")
    @ResponseBody
    public Map<String, Object> updateProblem(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            
            // Update problem information fields
            rma.setProblemDiscoverer(request.getParameter("problemDiscoverer"));
            rma.setWhatHappened(request.getParameter("whatHappened"));
            rma.setWhyAndHowItHappened(request.getParameter("whyAndHowItHappened"));
            rma.setHowContained(request.getParameter("howContained"));
            rma.setWhoContained(request.getParameter("whoContained"));
            
            // Parse problem discovery date
            String discoveryDateStr = request.getParameter("problemDiscoveryDate");
            if (discoveryDateStr != null && !discoveryDateStr.trim().isEmpty()) {
                try {
                    rma.setProblemDiscoveryDate(LocalDate.parse(discoveryDateStr));
                } catch (Exception e) {
                    logger.warn("Error parsing problem discovery date: {}", discoveryDateStr, e);
                }
            } else {
                rma.setProblemDiscoveryDate(null);
            }
            
            // Update process impact fields
            rma.setInterruptionToFlow("true".equals(request.getParameter("interruptionToFlow")));
            rma.setInterruptionToProduction("true".equals(request.getParameter("interruptionToProduction")));
            rma.setExposedToProcessGasOrChemicals("true".equals(request.getParameter("exposedToProcessGasOrChemicals")));
            rma.setPurged("true".equals(request.getParameter("purged")));
            
            // Parse downtime hours
            String downtimeStr = request.getParameter("downtimeHours");
            if (downtimeStr != null && !downtimeStr.trim().isEmpty()) {
                try {
                    rma.setDowntimeHours(Double.parseDouble(downtimeStr));
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing downtime hours: {}", downtimeStr, e);
                }
            } else {
                rma.setDowntimeHours(null);
            }
            
            rma.setInstructionsForExposedComponent(request.getParameter("instructionsForExposedComponent"));
            
            // Save the updated RMA
            rmaService.saveRma(rma, null);
            
            response.put("success", true);
            response.put("message", "Problem information updated successfully");
            
            logger.info("Updated problem information for RMA ID {}", id);
            
        } catch (Exception e) {
            logger.error("Error updating problem information for RMA ID " + id, e);
            response.put("success", false);
            response.put("message", "Error updating problem information: " + e.getMessage());
        }
        
        return response;
    }
    
    @PostMapping("/{id}/update-customer")
    @ResponseBody
    public Map<String, Object> updateCustomer(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            
            // Update customer information fields
            rma.setCustomerName(request.getParameter("customerName"));
            rma.setCompanyShipToName(request.getParameter("companyShipToName"));
            rma.setCompanyShipToAddress(request.getParameter("companyShipToAddress"));
            rma.setCity(request.getParameter("city"));
            rma.setState(request.getParameter("state"));
            rma.setZipCode(request.getParameter("zipCode"));
            rma.setAttn(request.getParameter("attn"));
            rma.setCustomerContact(request.getParameter("customerContact"));
            rma.setCustomerPhone(request.getParameter("customerPhone"));
            rma.setCustomerEmail(request.getParameter("customerEmail"));
            rma.setSalesOrder(request.getParameter("salesOrder"));
            
            // Update field technician information
            rma.setFieldTechName(request.getParameter("fieldTechName"));
            rma.setFieldTechPhone(request.getParameter("fieldTechPhone"));
            rma.setFieldTechEmail(request.getParameter("fieldTechEmail"));
            
            // Save the updated RMA
            rmaService.saveRma(rma, null);
            
            response.put("success", true);
            response.put("message", "Customer information updated successfully");
            
            logger.info("Updated customer information for RMA ID {}", id);
            
        } catch (Exception e) {
            logger.error("Error updating customer information for RMA ID " + id, e);
            response.put("success", false);
            response.put("message", "Error updating customer information: " + e.getMessage());
        }
        
        return response;
    }

    @PostMapping("/{id}/update-labor")
    public String updateLabor(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "RMA not found");
                return "redirect:/rma/" + id;
            }
            
            Rma rma = rmaOpt.get();
            
            // Clear existing labor entries
            rma.getLaborEntries().clear();
            
            // Process labor entries from form
            int laborIndex = 0;
            
            while (true) {
                String description = request.getParameter("laborEntries[" + laborIndex + "].description");
                String technician = request.getParameter("laborEntries[" + laborIndex + "].technician");
                String hoursStr = request.getParameter("laborEntries[" + laborIndex + "].hours");
                String laborDateStr = request.getParameter("laborEntries[" + laborIndex + "].laborDate");
                String pricePerHourStr = request.getParameter("laborEntries[" + laborIndex + "].pricePerHour");
                
                // Break if we've reached the end of labor entries
                if (description == null && technician == null && hoursStr == null) {
                    break;
                }
                
                // Skip empty entries
                if ((description == null || description.trim().isEmpty()) && 
                    (technician == null || technician.trim().isEmpty()) &&
                    (hoursStr == null || hoursStr.trim().isEmpty()) &&
                    (pricePerHourStr == null || pricePerHourStr.trim().isEmpty())) {
                    laborIndex++;
                    continue;
                }
                
                LaborEntry laborEntry = new LaborEntry();
                laborEntry.setDescription(description != null ? description.trim() : "");
                laborEntry.setTechnician(technician != null ? technician.trim() : "");
                
                // Parse hours
                if (hoursStr != null && !hoursStr.trim().isEmpty()) {
                    try {
                        laborEntry.setHours(new BigDecimal(hoursStr.trim()));
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid hours value: {}", hoursStr);
                        laborEntry.setHours(BigDecimal.ZERO);
                    }
                } else {
                    laborEntry.setHours(BigDecimal.ZERO);
                }
                
                // Parse labor date
                if (laborDateStr != null && !laborDateStr.trim().isEmpty()) {
                    try {
                        laborEntry.setLaborDate(LocalDate.parse(laborDateStr.trim()));
                    } catch (Exception e) {
                        logger.warn("Invalid labor date value: {}", laborDateStr);
                        laborEntry.setLaborDate(null);
                    }
                } else {
                    laborEntry.setLaborDate(null);
                }
                
                // Parse price per hour
                if (pricePerHourStr != null && !pricePerHourStr.trim().isEmpty()) {
                    try {
                        laborEntry.setPricePerHour(new BigDecimal(pricePerHourStr.trim()));
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid price per hour value: {}", pricePerHourStr);
                        laborEntry.setPricePerHour(BigDecimal.ZERO);
                    }
                } else {
                    laborEntry.setPricePerHour(BigDecimal.ZERO);
                }
                
                rma.getLaborEntries().add(laborEntry);
                laborIndex++;
            }
            
            // Save the updated RMA
            rmaService.saveRma(rma, null);
            
            redirectAttributes.addFlashAttribute("message", "Labor entries updated successfully");
            logger.info("Updated labor entries for RMA ID {}: {} entries", id, rma.getLaborEntries().size());
            
        } catch (Exception e) {
            logger.error("Error updating labor entries for RMA ID " + id, e);
            redirectAttributes.addFlashAttribute("error", "Error updating labor entries: " + e.getMessage());
        }
        
        return "redirect:/rma/" + id;
    }

    @PostMapping("/{id}/test-parts")
    @ResponseBody
    public Map<String, Object> testParts(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        logger.info("=== TEST PARTS REQUEST for RMA ID: {} ===", id);
        
        // Log all parameters received
        Map<String, String[]> paramMap = request.getParameterMap();
        logger.info("All request parameters:");
        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            logger.info("  {} = {}", entry.getKey(), java.util.Arrays.toString(entry.getValue()));
        }
        
        response.put("success", true);
        response.put("message", "Test endpoint reached successfully");
        response.put("paramCount", paramMap.size());
        
        return response;
    }

    @PostMapping("/{id}/debug-params")
    @ResponseBody
    public Map<String, Object> debugParams(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        logger.info("=== DEBUG PARAMS REQUEST for RMA ID: {} ===", id);
        
        // Log all parameters received
        Map<String, String[]> paramMap = request.getParameterMap();
        logger.info("Total parameters received: {}", paramMap.size());
        
        Map<String, Object> allParams = new HashMap<>();
        int partParamCount = 0;
        
        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            String paramName = entry.getKey();
            String[] values = entry.getValue();
            logger.info("  {} = {}", paramName, java.util.Arrays.toString(values));
            allParams.put(paramName, values.length == 1 ? values[0] : values);
            
            if (paramName.contains("partLineItems")) {
                partParamCount++;
            }
        }
        
        logger.info("Part-related parameters: {}", partParamCount);
        
        response.put("success", true);
        response.put("message", "Debug params logged successfully");
        response.put("totalParams", paramMap.size());
        response.put("partParams", partParamCount);
        response.put("allParams", allParams);
        
        return response;
    }

    /**
     * API endpoint to get comments for an RMA
     */
    @GetMapping("/api/rma/{id}/comments")
    @ResponseBody
    public Map<String, Object> getCommentsForRma(@PathVariable Long id) {
        logger.info("Loading comments for RMA ID: {}", id);
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<RmaComment> comments = rmaService.getCommentsForRma(id);
            logger.info("Found {} comments for RMA {}", comments.size(), id);
            List<Map<String, Object>> commentData = new ArrayList<>();
            
            for (RmaComment comment : comments) {
                Map<String, Object> commentMap = new HashMap<>();
                commentMap.put("content", comment.getContent());
                commentMap.put("createdDate", comment.getCreatedDate() != null ? 
                    comment.getCreatedDate().toString() : null);
                commentMap.put("author", comment.getUser() != null ? 
                    comment.getUser().getName() : "Unknown");
                commentData.add(commentMap);
            }
            
            result.put("success", true);
            result.put("comments", commentData);
            result.put("count", comments.size());
            
        } catch (Exception e) {
            logger.error("Error loading comments for RMA {}: {}", id, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * API endpoint to get problem details for an RMA tooltip
     */
    @GetMapping("/{id}/problem-details")
    @ResponseBody
    public Map<String, Object> getProblemDetailsForRma(@PathVariable Long id) {
        logger.info("Loading problem details for RMA ID: {}", id);
        Map<String, Object> result = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            
            if (rmaOpt.isPresent()) {
                Rma rma = rmaOpt.get();
                
                Map<String, Object> problemDetails = new HashMap<>();
                problemDetails.put("problemDiscoverer", rma.getProblemDiscoverer());
                problemDetails.put("problemDiscoveryDate", rma.getProblemDiscoveryDate() != null ? 
                    rma.getProblemDiscoveryDate().toString() : null);
                problemDetails.put("whatHappened", rma.getWhatHappened());
                problemDetails.put("whyAndHowItHappened", rma.getWhyAndHowItHappened());
                problemDetails.put("howContained", rma.getHowContained());
                problemDetails.put("whoContained", rma.getWhoContained());
                problemDetails.put("rmaNumber", rma.getRmaNumber() != null ? rma.getRmaNumber() : rma.getSapNotificationNumber());
                
                result.put("success", true);
                result.put("problemDetails", problemDetails);
                
                // Check if there are any actual problem details
                boolean hasDetails = rma.getProblemDiscoverer() != null || 
                                    rma.getProblemDiscoveryDate() != null ||
                                    rma.getWhatHappened() != null ||
                                    rma.getWhyAndHowItHappened() != null ||
                                    rma.getHowContained() != null ||
                                    rma.getWhoContained() != null;
                
                result.put("hasDetails", hasDetails);
                
            } else {
                result.put("success", false);
                result.put("error", "RMA not found");
            }
            
        } catch (Exception e) {
            logger.error("Error loading problem details for RMA {}: {}", id, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * API endpoint to get tool details for an RMA
     */
    @GetMapping("/api/rma/{id}/tool-details")
    @ResponseBody
    public Map<String, Object> getToolDetailsForRma(@PathVariable Long id) {
        logger.info("Loading tool details for RMA ID: {}", id);
        Map<String, Object> result = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isPresent()) {
                Rma rma = rmaOpt.get();
                
                if (rma.getTool() != null) {
                    Tool tool = rma.getTool();
                    Map<String, Object> toolDetails = new HashMap<>();
                    toolDetails.put("name", tool.getName());
                    toolDetails.put("toolType", tool.getToolType() != null ? tool.getToolType().getDisplayName() : "Unknown Type");
                    toolDetails.put("model1", tool.getModel1());
                    toolDetails.put("model2", tool.getModel2());
                    toolDetails.put("serialNumber1", tool.getSerialNumber1());
                    toolDetails.put("serialNumber2", tool.getSerialNumber2());
                    
                    result.put("toolDetails", toolDetails);
                    result.put("hasTool", true);
                } else {
                    result.put("hasTool", false);
                }
                
                result.put("success", true);
                logger.info("Successfully loaded tool details for RMA {}, hasTool: {}", id, rma.getTool() != null);
            } else {
                result.put("success", false);
                result.put("error", "RMA not found");
                logger.warn("RMA not found for ID: {}", id);
            }
        } catch (Exception e) {
            logger.error("Error loading tool details for RMA {}: {}", id, e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Failed to load tool details: " + e.getMessage());
        }
        
        return result;
    }



    @GetMapping("/api/test-moving-parts/{id}")
    @ResponseBody
    public Map<String, Object> testMovingParts(@PathVariable Long id) {
        logger.info("Loading moving parts for RMA ID: {}", id);
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Use the EXACT same logic as the working RMA detail page
            List<MovingPart> movingParts = movingPartService.getMovingPartsByRmaId(id);
            List<Map<String, Object>> movingPartsData = new ArrayList<>();
            
            for (MovingPart part : movingParts) {
                Map<String, Object> partData = new HashMap<>();
                partData.put("id", part.getId());
                partData.put("partName", part.getPartName());
                partData.put("partNumber", part.getPartNumber());
                partData.put("fromLocation", part.getFromTool() != null ? part.getFromTool().getName() : "Unknown");
                
                // Get destination from destination chain (use the last destination as current location)
                Long currentLocationId = part.getCurrentLocationToolId();
                String toLocation = "Unknown";
                if (currentLocationId != null) {
                    try {
                        Optional<Tool> destTool = toolService.getToolById(currentLocationId);
                        toLocation = destTool.map(Tool::getName).orElse("Unknown");
                    } catch (Exception e) {
                        logger.warn("Could not fetch destination tool for ID: {}", currentLocationId);
                    }
                }
                partData.put("toLocation", toLocation);
                partData.put("movementDate", part.getMoveDate() != null ? part.getMoveDate().toString() : null);
                partData.put("notes", part.getNotes());
                
                movingPartsData.add(partData);
            }
            
            result.put("success", true);
            result.put("movingParts", movingPartsData);
            logger.info("Successfully loaded {} moving parts for RMA: {}", movingPartsData.size(), id);
            
        } catch (Exception e) {
            logger.error("Error loading moving parts for RMA {}: {}", id, e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Failed to load moving parts: " + e.getMessage());
        }
        
        return result;
    }

    @GetMapping("/api/upload-diagnostic/{id}")
    @ResponseBody
    public Map<String, Object> uploadDiagnostic(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get upload directory info
            result.put("uploadDir", uploadUtils.getUploadDir());
            result.put("uploadDirAbsolute", new File(uploadUtils.getUploadDir()).getAbsolutePath());
            result.put("uploadDirExists", new File(uploadUtils.getUploadDir()).exists());
            
            // Check RMA and its files
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isPresent()) {
                Rma rma = rmaOpt.get();
                result.put("rmaId", rma.getId());
                result.put("rmaNumber", rma.getRmaNumber());
                
                // Check documents
                List<Map<String, Object>> docInfo = new ArrayList<>();
                if (rma.getDocuments() != null) {
                    for (RmaDocument doc : rma.getDocuments()) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", doc.getId());
                        info.put("fileName", doc.getFileName());
                        info.put("storedPath", doc.getFilePath());
                        
                        // Check different path resolutions
                        File f1 = new File(doc.getFilePath());
                        info.put("absolutePathExists", f1.exists());
                        info.put("absolutePath", f1.getAbsolutePath());
                        
                        File f2 = new File(uploadUtils.getUploadDir(), doc.getFilePath());
                        info.put("relativePathExists", f2.exists());
                        info.put("relativePath", f2.getAbsolutePath());
                        
                        docInfo.add(info);
                    }
                }
                result.put("documents", docInfo);
                result.put("documentCount", docInfo.size());
                
                // Check pictures
                List<Map<String, Object>> picInfo = new ArrayList<>();
                if (rma.getPictures() != null) {
                    for (RmaPicture pic : rma.getPictures()) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", pic.getId());
                        info.put("fileName", pic.getFileName());
                        info.put("storedPath", pic.getFilePath());
                        
                        File f1 = new File(pic.getFilePath());
                        info.put("absolutePathExists", f1.exists());
                        info.put("absolutePath", f1.getAbsolutePath());
                        
                        File f2 = new File(uploadUtils.getUploadDir(), pic.getFilePath());
                        info.put("relativePathExists", f2.exists());
                        info.put("relativePath", f2.getAbsolutePath());
                        
                        picInfo.add(info);
                    }
                }
                result.put("pictures", picInfo);
                result.put("pictureCount", picInfo.size());
            }
            
            // Check subdirectories
            String[] subdirs = {"rma-pictures", "rma-documents"};
            Map<String, Object> subdirInfo = new HashMap<>();
            for (String subdir : subdirs) {
                File dir = new File(uploadUtils.getUploadDir(), subdir);
                subdirInfo.put(subdir + "_exists", dir.exists());
                subdirInfo.put(subdir + "_path", dir.getAbsolutePath());
            }
            result.put("subdirectories", subdirInfo);
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.error("Upload diagnostic error: ", e);
        }
        
        return result;
    }
    
    /**
     * Handle temporary moving parts data from the form submission
     */
    private void handleTempMovingParts(HttpServletRequest request, Rma savedRma) {
        try {
            // Look for temporary moving parts parameters
            Map<String, String[]> parameterMap = request.getParameterMap();
            Map<Integer, Map<String, String>> tempMovingParts = new HashMap<>();
            
            // Check for new array-based format first (movingPartNames[], movingPartFroms[], etc.)
            String[] partNames = request.getParameterValues("movingPartNames");
            String[] partFroms = request.getParameterValues("movingPartFroms");
            String[] partTos = request.getParameterValues("movingPartTos");
            String[] partNotes = request.getParameterValues("movingPartNotes");
            
            if (partNames != null && partNames.length > 0) {
                logger.info("Found {} moving parts in new array format", partNames.length);
                // Use new array-based format
                for (int i = 0; i < partNames.length; i++) {
                    Map<String, String> partData = new HashMap<>();
                    partData.put("partName", partNames[i]);
                    partData.put("from", partFroms != null && i < partFroms.length ? partFroms[i] : "");
                    partData.put("to", partTos != null && i < partTos.length ? partTos[i] : "");
                    partData.put("notes", partNotes != null && i < partNotes.length ? partNotes[i] : "");
                    tempMovingParts.put(i, partData);
                }
            } else {
                // Fallback to old tempMovingParts[index].field format
                // Group parameters by index
                for (String paramName : parameterMap.keySet()) {
                    if (paramName.startsWith("tempMovingParts[")) {
                        // Extract index and field name
                        int startIndex = paramName.indexOf('[') + 1;
                        int endIndex = paramName.indexOf(']');
                        int dotIndex = paramName.indexOf('.', endIndex);
                        
                        if (startIndex > 0 && endIndex > startIndex && dotIndex > endIndex) {
                            try {
                                int index = Integer.parseInt(paramName.substring(startIndex, endIndex));
                                String fieldName = paramName.substring(dotIndex + 1);
                                String value = request.getParameter(paramName);
                                
                                tempMovingParts.computeIfAbsent(index, k -> new HashMap<>()).put(fieldName, value);
                            } catch (NumberFormatException e) {
                                logger.warn("Invalid index in temp moving part parameter: {}", paramName);
                            }
                        }
                    }
                }
            }
            
            // Create moving parts from the temporary data
            for (Map<String, String> partData : tempMovingParts.values()) {
                String partName = partData.get("partName");
                String from = partData.get("from"); // Can be: toolId, "CL_locationId", "SESSION_locationName", or "CUSTOM:locationName"
                String to = partData.get("to");     // Same format
                String notes = partData.get("notes");
                
                // Fallback to old format
                if (from == null) from = partData.get("fromToolId");
                if (to == null) to = partData.get("destinationToolIds");
                
                // Validate we have part name and from/to
                if (partName != null && !partName.trim().isEmpty() && from != null && to != null) {
                    try {
                        // Parse "from" location
                        Long fromToolId = null;
                        String fromCustomLocation = null;
                        
                        if (from.startsWith("CUSTOM:")) {
                            fromCustomLocation = from.substring(7); // Remove "CUSTOM:" prefix
                        } else if (from.startsWith("CL_")) {
                            // Custom location entity ID - for now, treat as custom text
                            // TODO: Link to CustomLocation entity
                            fromCustomLocation = from.substring(3);
                        } else if (from.startsWith("SESSION_")) {
                            fromCustomLocation = from.substring(8); // Remove "SESSION_" prefix
                        } else if (!from.equals("CUSTOM") && !from.trim().isEmpty()) {
                            fromToolId = Long.parseLong(from);
                        }
                        
                        // Parse "to" location
                        List<Long> destinationToolIds = new ArrayList<>();
                        List<String> toCustomLocations = new ArrayList<>();
                        
                        if (to.startsWith("CUSTOM:")) {
                            toCustomLocations.add(to.substring(7));
                        } else if (to.startsWith("CL_")) {
                            // Custom location entity ID - for now, treat as custom text
                            // TODO: Link to CustomLocation entity
                            toCustomLocations.add(to.substring(3));
                        } else if (to.startsWith("SESSION_")) {
                            toCustomLocations.add(to.substring(8));
                        } else if (!to.equals("CUSTOM") && !to.trim().isEmpty()) {
                            destinationToolIds.add(Long.parseLong(to));
                        }
                        
                        // Create the moving part
                        MovingPart movingPart = movingPartService.createMovingPart(
                            partName.trim(), fromToolId, fromCustomLocation, 
                            destinationToolIds, toCustomLocations, notes, null, savedRma);
                        
                        logger.info("Created moving part '{}' for RMA {} (from={}, to={})", 
                                    partName, savedRma.getId(), from, to);
                        
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid tool ID in temp moving part data: from={}, to={}", from, to);
                    } catch (Exception e) {
                        logger.error("Error creating moving part '{}' for RMA {}: {}", 
                                    partName, savedRma.getId(), e.getMessage(), e);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error processing temporary moving parts for RMA {}: {}", savedRma.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Export filtered RMA list to Excel
     */
    @PostMapping("/export-excel")
    public ResponseEntity<byte[]> exportFilteredRmasToExcel(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String statusFilters,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            Principal principal) {
        
        try {
            logger.info("Exporting filtered RMA list to Excel");
            logger.info("Filters - searchTerm: '{}', statusFilters: '{}', sortBy: '{}', sortDirection: '{}'", 
                       searchTerm, statusFilters, sortBy, sortDirection);
            
            // Get all RMAs first
            List<Rma> allRmas = rmaService.getAllRmas();
            
            // Apply filters
            List<Rma> filteredRmas = filterRmas(allRmas, searchTerm, statusFilters, sortBy, sortDirection);
            
            logger.info("Filtered {} RMAs out of {} total RMAs for export", filteredRmas.size(), allRmas.size());
            
            // Generate Excel file
            byte[] excelData = excelService.generateRmaListExcel(filteredRmas);
            
            // Generate filename with timestamp
            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .format(java.time.LocalDateTime.now());
            String filename = "RMA_List_Export_" + timestamp + ".xlsx";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(excelData.length);
            
            logger.info("Successfully exported {} RMAs to Excel file: {}", filteredRmas.size(), filename);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);
                    
        } catch (Exception e) {
            logger.error("Error exporting RMA list to Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error generating Excel export: " + e.getMessage()).getBytes());
        }
    }
    
    /**
     * Filter RMAs based on search criteria
     */
    private List<Rma> filterRmas(List<Rma> rmas, String searchTerm, String statusFiltersJson, String sortBy, String sortDirection) {
        List<Rma> filtered = new ArrayList<>(rmas);
        
        // Apply search term filter
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String searchLower = searchTerm.toLowerCase().trim();
            filtered = filtered.stream()
                    .filter(rma -> matchesSearchTerm(rma, searchLower))
                    .collect(Collectors.toList());
        }
        
        // Apply status filters
        if (statusFiltersJson != null && !statusFiltersJson.trim().isEmpty() && !statusFiltersJson.equals("[]")) {
            try {
                // Parse JSON array of status strings
                List<String> statusFilters = parseStatusFilters(statusFiltersJson);
                if (!statusFilters.isEmpty()) {
                    Set<RmaStatus> allowedStatuses = statusFilters.stream()
                            .map(status -> {
                                try {
                                    return RmaStatus.valueOf(status);
                                } catch (IllegalArgumentException e) {
                                    logger.warn("Invalid status filter: {}", status);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    
                    filtered = filtered.stream()
                            .filter(rma -> allowedStatuses.contains(rma.getStatus()))
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                logger.warn("Error parsing status filters: {}", e.getMessage());
            }
        }
        
        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            boolean ascending = "asc".equalsIgnoreCase(sortDirection);
            filtered = sortRmas(filtered, sortBy, ascending);
        }
        
        return filtered;
    }
    
    /**
     * Check if RMA matches search term
     */
    private boolean matchesSearchTerm(Rma rma, String searchTerm) {
        // Search in RMA number
        if (rma.getRmaNumber() != null && rma.getRmaNumber().toLowerCase().contains(searchTerm)) {
            return true;
        }
        
        // Search in tool name
        if (rma.getTool() != null && rma.getTool().getName() != null && 
            rma.getTool().getName().toLowerCase().contains(searchTerm)) {
            return true;
        }
        
        // Search in customer name
        if (rma.getCustomerName() != null && rma.getCustomerName().toLowerCase().contains(searchTerm)) {
            return true;
        }
        
        // Search in part line items
        if (rma.getPartLineItems() != null) {
            for (PartLineItem item : rma.getPartLineItems()) {
                if ((item.getPartName() != null && item.getPartName().toLowerCase().contains(searchTerm)) ||
                    (item.getPartNumber() != null && item.getPartNumber().toLowerCase().contains(searchTerm)) ||
                    (item.getProductDescription() != null && item.getProductDescription().toLowerCase().contains(searchTerm))) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Parse status filters from JSON string
     */
    private List<String> parseStatusFilters(String statusFiltersJson) {
        try {
            // Remove brackets and quotes, split by comma
            String cleaned = statusFiltersJson.replaceAll("[\\[\\]\"]", "");
            if (cleaned.trim().isEmpty()) {
                return new ArrayList<>();
            }
            return Arrays.stream(cleaned.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Error parsing status filters: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Sort RMAs based on sort criteria
     */
    private List<Rma> sortRmas(List<Rma> rmas, String sortBy, boolean ascending) {
        Comparator<Rma> comparator;
        
        switch (sortBy.toLowerCase()) {
            case "rma-number":
                comparator = Comparator.comparing(rma -> rma.getRmaNumber(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "tool":
                comparator = Comparator.comparing(rma -> rma.getTool() != null ? rma.getTool().getName() : "", 
                                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "status":
                comparator = Comparator.comparing(rma -> rma.getStatus() != null ? rma.getStatus().name() : "", 
                                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "customer":
                comparator = Comparator.comparing(rma -> rma.getCustomerName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "date":
            default:
                comparator = Comparator.comparing(rma -> rma.getWrittenDate(), Comparator.nullsLast(Comparator.naturalOrder()));
                break;
        }
        
        if (!ascending) {
            comparator = comparator.reversed();
        }
        
        return rmas.stream().sorted(comparator).collect(Collectors.toList());
    }

    @PostMapping("/{id}/update-location")
    @ResponseBody
    public Map<String, Object> updateRmaLocation(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }

            Rma rma = rmaOpt.get();
            String locationIdStr = request.getParameter("locationId");
            
            if (locationIdStr != null && !locationIdStr.trim().isEmpty()) {
                Long locationId = Long.parseLong(locationIdStr);
                Optional<Location> locationOpt = locationService.getLocationById(locationId);
                if (locationOpt.isPresent()) {
                    rma.setLocation(locationOpt.get());
                } else {
                    response.put("success", false);
                    response.put("message", "Location not found");
                    return response;
                }
            } else {
                rma.setLocation(null);
            }

            rmaService.saveRma(rma, null);
            
            response.put("success", true);
            response.put("message", "Location updated successfully");
            response.put("locationName", rma.getLocation() != null ? rma.getLocation().getDisplayName() : "No location");
            
        } catch (Exception e) {
            logger.error("Error updating RMA location", e);
            response.put("success", false);
            response.put("message", "Error updating location: " + e.getMessage());
        }
        
        return response;
    }

    @PostMapping("/{id}/update-tool")
    @ResponseBody
    public Map<String, Object> updateRmaTool(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }

            Rma rma = rmaOpt.get();
            String toolIdStr = request.getParameter("toolId");
            
            if (toolIdStr != null && !toolIdStr.trim().isEmpty()) {
                Long toolId = Long.parseLong(toolIdStr);
                Optional<Tool> toolOpt = toolService.getToolById(toolId);
                if (toolOpt.isPresent()) {
                    rma.setTool(toolOpt.get());
                } else {
                    response.put("success", false);
                    response.put("message", "Tool not found");
                    return response;
                }
            } else {
                rma.setTool(null);
            }

            rmaService.saveRma(rma, null);
            
            response.put("success", true);
            response.put("message", "Tool updated successfully");
            response.put("toolName", rma.getTool() != null ? rma.getTool().getName() : "No tool assigned");
            
        } catch (Exception e) {
            logger.error("Error updating RMA tool", e);
            response.put("success", false);
            response.put("message", "Error updating tool: " + e.getMessage());
        }
        
        return response;
    }



    @PostMapping("/{id}/update-process-impact")
    @ResponseBody
    public Map<String, Object> updateProcessImpact(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }

            Rma rma = rmaOpt.get();
            
            // Update process impact fields
            String interruptionToFlow = request.getParameter("interruptionToFlow");
            if (interruptionToFlow != null) {
                rma.setInterruptionToFlow("true".equals(interruptionToFlow));
            }
            
            String interruptionToProduction = request.getParameter("interruptionToProduction");
            if (interruptionToProduction != null) {
                rma.setInterruptionToProduction("true".equals(interruptionToProduction));
            }
            
            String downtimeHoursStr = request.getParameter("downtimeHours");
            if (downtimeHoursStr != null && !downtimeHoursStr.trim().isEmpty()) {
                try {
                    rma.setDowntimeHours(Double.parseDouble(downtimeHoursStr));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid number format for downtimeHours: {}", downtimeHoursStr);
                    rma.setDowntimeHours(null);
                }
            } else {
                rma.setDowntimeHours(null);
            }
            
            String exposedToProcessGasOrChemicals = request.getParameter("exposedToProcessGasOrChemicals");
            if (exposedToProcessGasOrChemicals != null) {
                rma.setExposedToProcessGasOrChemicals("true".equals(exposedToProcessGasOrChemicals));
            }
            
            String purged = request.getParameter("purged");
            if (purged != null) {
                rma.setPurged("true".equals(purged));
            }
            
            String startupSo3Complete = request.getParameter("startupSo3Complete");
            if (startupSo3Complete != null) {
                rma.setStartupSo3Complete("true".equals(startupSo3Complete));
            }
            
            String failedOnInstall = request.getParameter("failedOnInstall");
            if (failedOnInstall != null) {
                rma.setFailedOnInstall("true".equals(failedOnInstall));
            }
            
            String instructionsForExposedComponent = request.getParameter("instructionsForExposedComponent");
            if (instructionsForExposedComponent != null) {
                rma.setInstructionsForExposedComponent(instructionsForExposedComponent.trim().isEmpty() ? null : instructionsForExposedComponent.trim());
            }

            rmaService.saveRma(rma, null);
            
            response.put("success", true);
            response.put("message", "Process impact updated successfully");
            
        } catch (Exception e) {
            logger.error("Error updating process impact for RMA {}", id, e);
            response.put("success", false);
            response.put("message", "Error updating process impact: " + e.getMessage());
        }
        
        return response;
    }
} 