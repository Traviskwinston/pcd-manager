package com.pcd.manager.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcd.manager.model.Location;
import com.pcd.manager.model.MapGridItem;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.model.User;
import com.pcd.manager.model.Note;
import com.pcd.manager.repository.LocationRepository;
import com.pcd.manager.repository.PassdownRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.TrackTrendRepository;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.repository.ToolCommentRepository;
import com.pcd.manager.service.MapGridService;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.TrackTrendService;
import com.pcd.manager.service.NoteService;
import com.pcd.manager.service.UserService;
import com.pcd.manager.service.DashboardService;
import com.pcd.manager.service.AsyncDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private final ToolRepository toolRepository;
    private final PassdownRepository passdownRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final MapGridService mapGridService;
    private final ObjectMapper objectMapper;
    private final ToolService toolService;
    private final TrackTrendService trackTrendService;
    private final NoteService noteService;
    private final UserService userService;
    private final RmaRepository rmaRepository;
    private final ToolCommentRepository toolCommentRepository;
    private final TrackTrendRepository trackTrendRepository;
    private final DashboardService dashboardService;
    private final AsyncDataService asyncDataService;

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @Autowired
    public DashboardController(ToolRepository toolRepository,
                             PassdownRepository passdownRepository,
                             LocationRepository locationRepository,
                             UserRepository userRepository,
                             MapGridService mapGridService,
                             ObjectMapper objectMapper,
                             ToolService toolService,
                             TrackTrendService trackTrendService,
                             NoteService noteService,
                             UserService userService,
                             RmaRepository rmaRepository,
                             ToolCommentRepository toolCommentRepository,
                             TrackTrendRepository trackTrendRepository,
                             DashboardService dashboardService,
                             AsyncDataService asyncDataService) {
        this.toolRepository = toolRepository;
        this.passdownRepository = passdownRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.mapGridService = mapGridService;
        this.objectMapper = objectMapper;
        this.toolService = toolService;
        this.trackTrendService = trackTrendService;
        this.noteService = noteService;
        this.userService = userService;
        this.rmaRepository = rmaRepository;
        this.toolCommentRepository = toolCommentRepository;
        this.trackTrendRepository = trackTrendRepository;
        this.dashboardService = dashboardService;
        this.asyncDataService = asyncDataService;
    }

    @GetMapping
    public String showDashboard(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
             return "redirect:/login";
        }
        String userEmail = authentication.getName(); // Email is used as username
        User user = userRepository.findByEmailIgnoreCase(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userEmail));

        // Ensure user has an active site, set to system default if not
        Location effectiveUserLocation = null;
        if (user.getActiveSite() != null) {
            effectiveUserLocation = user.getActiveSite();
        } else {
            Optional<Location> defaultLocationOpt = locationRepository.findByDefaultLocationIsTrue();
            if (defaultLocationOpt.isPresent()) {
                effectiveUserLocation = defaultLocationOpt.get();
                user.setActiveSite(effectiveUserLocation);
                try {
                    userService.updateUser(user); // Save the user with the updated active site
                    logger.info("User {} had no active site, set to system default and saved: {}", userEmail, effectiveUserLocation.getDisplayName());
                } catch (Exception e) {
                    logger.error("Failed to save user {} after setting active site: {}", userEmail, e.getMessage());
                    // Continue without saving if there's an issue, but log it.
                }
            } else {
                logger.warn("User {} has no active site, and no system default location is configured! Grid items may be incorrect.", userEmail);
            }
        }

        // Determine the location for which to fetch grid items
        Long currentLocationId = null;
        if (effectiveUserLocation != null) {
            currentLocationId = effectiveUserLocation.getId();
            logger.info("Using location ID {} for grid items for user {}.", currentLocationId, userEmail);
        } else {
            logger.warn("No effective location determined for user {}. Grid items will likely be empty.", userEmail);
        }

        List<MapGridItem> gridItems = mapGridService.getGridItemsByLocationId(currentLocationId);
        logger.info("Fetched {} grid items for facility map for location ID: {}", gridItems.size(), currentLocationId);

        // Fetch tools with optimized loading - only loads location and technicians (not heavy collections like tags)
        // This is used for the dashboard list with 4 icon columns that need Tool objects
        // Filter tools by the current user's location
        List<Tool> allTools;
        if (currentLocationId != null) {
            // Find location name from locationId to use with new method
            Optional<Location> locationOpt = locationRepository.findById(currentLocationId);
            if (locationOpt.isPresent()) {
                String locationName = locationOpt.get().getDisplayName() != null ? 
                                    locationOpt.get().getDisplayName() : locationOpt.get().getName();
                allTools = toolRepository.findByLocationNameForDashboardView(locationName);
            } else {
                allTools = new ArrayList<>();
            }
            logger.info("Fetched {} tools for dashboard filtered by location ID: {}", allTools.size(), currentLocationId);
        } else {
            allTools = toolRepository.findAllForDashboardView();
            logger.info("No location filter applied, fetched {} tools for dashboard", allTools.size());
        }

        // Sort tools: Assigned tools first, then by name
        List<Tool> sortedTools = allTools.stream()
                .sorted(Comparator.<Tool, Boolean>comparing(t -> t.getCurrentTechnicians().isEmpty())
                        .thenComparing(Tool::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        logger.info("Fetched and sorted {} tools for dashboard list.", sortedTools.size());

        // TODO: Remove this temporary logging after confirming assigned tool logic works
        long assignedToolCount = sortedTools.stream().filter(t -> !t.getCurrentTechnicians().isEmpty()).count();
        logger.info("Temporary Check: Number of tools with assigned technicians: {}", assignedToolCount);

        // ASYNC OPTIMIZATION: Load dashboard data in parallel
        long dashboardStartTime = System.currentTimeMillis();
        
        // Initialize variables outside try block to ensure they're accessible later
        List<Passdown> recentPassdowns = new ArrayList<>();
        List<String> passdownUsers = new ArrayList<>();
        List<String> passdownTools = new ArrayList<>();
        List<Map<String, Object>> allToolsData = new ArrayList<>();
        List<Map<String, Object>> formattedTrackTrends = new ArrayList<>();
        
        try {
            logger.info("Starting async dashboard data loading");
            
            CompletableFuture<Map<String, Object>> dashboardDataFuture = 
                asyncDataService.loadDashboardDataAsync();
            
            // Wait for async operations to complete
            Map<String, Object> dashboardData = dashboardDataFuture.get();
            
            // Extract the data with proper casting
            @SuppressWarnings("unchecked")
            List<Passdown> asyncRecentPassdowns = (List<Passdown>) dashboardData.get("recentPassdowns");
            @SuppressWarnings("unchecked")
            List<String> asyncPassdownUsers = (List<String>) dashboardData.get("passdownUsers");
            @SuppressWarnings("unchecked")
            List<String> asyncPassdownTools = (List<String>) dashboardData.get("passdownTools");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> asyncAllToolsData = (List<Map<String, Object>>) dashboardData.get("allToolsData");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> asyncFormattedTrackTrends = (List<Map<String, Object>>) dashboardData.get("formattedTrackTrends");
            
            // Assign to our variables
            recentPassdowns = asyncRecentPassdowns != null ? asyncRecentPassdowns : new ArrayList<>();
            passdownUsers = asyncPassdownUsers != null ? asyncPassdownUsers : new ArrayList<>();
            passdownTools = asyncPassdownTools != null ? asyncPassdownTools : new ArrayList<>();
            allToolsData = asyncAllToolsData != null ? asyncAllToolsData : new ArrayList<>();
            formattedTrackTrends = asyncFormattedTrackTrends != null ? asyncFormattedTrackTrends : new ArrayList<>();
            
            long dashboardDuration = System.currentTimeMillis() - dashboardStartTime;
            logger.info("Completed async dashboard data loading in {}ms", dashboardDuration);
            
        } catch (Exception e) {
            logger.error("Error in async dashboard data loading, using fallback: {}", e.getMessage(), e);
            
            // Fallback to synchronous loading if async fails
            LocalDate twoWeeksAgo = LocalDate.now().minusWeeks(2);
            LocalDate today = LocalDate.now();
            recentPassdowns = passdownRepository.findByDateBetweenOrderByDateDesc(twoWeeksAgo, today);
            
            passdownUsers = recentPassdowns.stream()
                    .map(pd -> pd.getUser().getName())
                    .distinct()
                    .collect(Collectors.toList());
            passdownTools = recentPassdowns.stream()
                    .map(pd -> pd.getTool() != null ? pd.getTool().getName() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            
            List<Object[]> gridToolData = toolRepository.findGridViewData();
            allToolsData = gridToolData.stream().map(row -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("name", row[1]);
                map.put("model", row[2]);
                map.put("serial", row[3]);
                map.put("status", row[4] != null ? row[4].toString() : "");
                map.put("type", row[5] != null ? row[5].toString() : "");
                map.put("location", row[6]);
                map.put("hasAssignedUsers", row[7]);
                return map;
            }).collect(Collectors.toList());
            
            List<TrackTrend> allTrackTrends = trackTrendService.getAllTrackTrendsWithAffectedTools();
            formattedTrackTrends = allTrackTrends.stream().map(tt -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", tt.getId());
                map.put("title", tt.getName());
                List<Long> affectedToolIds = tt.getAffectedTools().stream()
                        .map(Tool::getId)
                        .collect(Collectors.toList());
                map.put("affectedTools", affectedToolIds);
                return map;
            }).collect(Collectors.toList());
        }
        
        // Load lightweight data for the 4 icon columns (same as optimized tools list)
        List<Long> toolIds = sortedTools.stream().map(Tool::getId).collect(Collectors.toList());
        
        // Initialize all maps to ensure they're never null
        Map<Long, List<Map<String, Object>>> toolRmasMap = new HashMap<>();
        Map<Long, List<Map<String, Object>>> toolPassdownsMap = new HashMap<>();
        Map<Long, List<Map<String, Object>>> toolCommentsMap = new HashMap<>();
        Map<Long, List<Map<String, Object>>> toolTrackTrendsMap = new HashMap<>();
        
        if (!toolIds.isEmpty()) {
            // Bulk load lightweight RMA data
            try {
                List<Object[]> rmaData = rmaRepository.findRmaListDataByToolIds(toolIds);
                for (Object[] row : rmaData) {
                    Long rmaId = (Long) row[0];
                    String rmaNumber = (String) row[1];
                    Object status = row[2];
                    Long toolId = (Long) row[3];
                    
                    Map<String, Object> rmaInfo = new HashMap<>();
                    rmaInfo.put("id", rmaId);
                    rmaInfo.put("rmaNumber", rmaNumber);
                    rmaInfo.put("status", status);
                    
                    toolRmasMap.computeIfAbsent(toolId, k -> new ArrayList<>()).add(rmaInfo);
                }
            } catch (Exception e) {
                logger.error("Error loading lightweight RMA data for dashboard: {}", e.getMessage(), e);
            }
            
            // Bulk load lightweight Passdown data
            try {
                List<Object[]> passdownData = passdownRepository.findPassdownListDataByToolIds(toolIds);
                for (Object[] row : passdownData) {
                    Long passdownId = (Long) row[0];
                    Object date = row[1];
                    String userName = (String) row[2];
                    String comment = (String) row[3];
                    Long toolId = (Long) row[4];
                    
                    Map<String, Object> passdownInfo = new HashMap<>();
                    passdownInfo.put("id", passdownId);
                    passdownInfo.put("date", date);
                    passdownInfo.put("userName", userName);
                    passdownInfo.put("comment", comment);
                    
                    toolPassdownsMap.computeIfAbsent(toolId, k -> new ArrayList<>()).add(passdownInfo);
                }
            } catch (Exception e) {
                logger.error("Error loading lightweight Passdown data for dashboard: {}", e.getMessage(), e);
            }
            
            // Bulk load lightweight Comment data
            try {
                List<Object[]> commentData = toolCommentRepository.findCommentListDataByToolIds(toolIds);
                for (Object[] row : commentData) {
                    Long commentId = (Long) row[0];
                    Object createdDate = row[1];
                    String userName = (String) row[2];
                    String content = (String) row[3];
                    Long toolId = (Long) row[4];
                    
                    Map<String, Object> commentInfo = new HashMap<>();
                    commentInfo.put("id", commentId);
                    commentInfo.put("createdDate", createdDate);
                    commentInfo.put("userName", userName);
                    commentInfo.put("content", content);
                    
                    toolCommentsMap.computeIfAbsent(toolId, k -> new ArrayList<>()).add(commentInfo);
                }
            } catch (Exception e) {
                logger.error("Error loading lightweight Comment data for dashboard: {}", e.getMessage(), e);
            }
            
            // Bulk load lightweight Track/Trend data
            try {
                List<Object[]> trackTrendData = trackTrendRepository.findTrackTrendListDataByToolIds(toolIds);
                for (Object[] row : trackTrendData) {
                    Long trackTrendId = (Long) row[0];
                    String name = (String) row[1];
                    Long toolId = (Long) row[2];
                    
                    Map<String, Object> trackTrendInfo = new HashMap<>();
                    trackTrendInfo.put("id", trackTrendId);
                    trackTrendInfo.put("name", name);
                    
                    toolTrackTrendsMap.computeIfAbsent(toolId, k -> new ArrayList<>()).add(trackTrendInfo);
                }
            } catch (Exception e) {
                logger.error("Error loading lightweight Track/Trend data for dashboard: {}", e.getMessage(), e);
            }
            
            // Ensure all tools have entries in maps (even if empty)
            for (Long toolId : toolIds) {
                toolRmasMap.computeIfAbsent(toolId, k -> new ArrayList<>());
                toolPassdownsMap.computeIfAbsent(toolId, k -> new ArrayList<>());
                toolCommentsMap.computeIfAbsent(toolId, k -> new ArrayList<>());
                toolTrackTrendsMap.computeIfAbsent(toolId, k -> new ArrayList<>());
            }
        }
        
        // OPTIMIZATION: Bulk load all notes for all tools to avoid N+1 queries
        List<Note> allNotes = noteService.getNotesByToolIds(toolIds);
        logger.info("Bulk loaded {} notes for {} tools (avoiding N+1 queries)", allNotes.size(), toolIds.size());
        
        // Group notes by tool ID for efficient lookup
        Map<Long, List<Note>> notesByToolId = allNotes.stream()
                .collect(Collectors.groupingBy(note -> note.getTool().getId()));
        
        // Prepare tools for display with searchable notes using bulk-loaded data
        List<Map<String, Object>> toolDisplayList = sortedTools.stream().map(tool -> {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("tool", tool); 
            
            // Get notes from bulk-loaded map instead of individual queries
            List<Note> actualNotes = notesByToolId.getOrDefault(tool.getId(), List.of());
            String concatenatedNoteContent = actualNotes.stream()
                                                .map(Note::getContent)
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.joining(" \n ")); // Join with space or newline
            String toolOwnNotes = tool.getNotes() != null ? tool.getNotes() : "";
            String searchableNotes = (toolOwnNotes + " " + concatenatedNoteContent).trim();
            toolMap.put("searchableNotes", searchableNotes);
            return toolMap;
        }).collect(Collectors.toList());
        
        // Add data needed for the dashboard template
        model.addAttribute("tools", toolDisplayList);
        model.addAttribute("recentPassdowns", recentPassdowns);
        model.addAttribute("passdownUsers", passdownUsers);
        model.addAttribute("passdownTools", passdownTools);
        model.addAttribute("locations", locationRepository.findAll());
        model.addAttribute("currentUser", user);
        model.addAttribute("gridItems", gridItems);
        model.addAttribute("allToolsData", allToolsData);
        model.addAttribute("allTrackTrends", formattedTrackTrends);
        // Add the 4 icon column data maps
        model.addAttribute("toolRmasMap", toolRmasMap);
        model.addAttribute("toolPassdownsMap", toolPassdownsMap);
        model.addAttribute("toolCommentsMap", toolCommentsMap);
        model.addAttribute("toolTrackTrendsMap", toolTrackTrendsMap);

        return "dashboard";
    }
} 