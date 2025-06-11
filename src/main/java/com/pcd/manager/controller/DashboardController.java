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
import com.pcd.manager.service.MapGridService;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.TrackTrendService;
import com.pcd.manager.service.NoteService;
import com.pcd.manager.service.UserService;
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Optional;

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
                             UserService userService) {
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

        // Fetch all tools with ALL related entities eagerly loaded to prevent N+1 queries
        List<Tool> allTools = toolRepository.findAllWithAllRelations();

        // Sort tools: Assigned tools first, then by name
        List<Tool> sortedTools = allTools.stream()
                .sorted(Comparator.<Tool, Boolean>comparing(t -> t.getCurrentTechnicians().isEmpty())
                        .thenComparing(Tool::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        logger.info("Fetched and sorted {} tools for dashboard list.", sortedTools.size());

        // TODO: Remove this temporary logging after confirming assigned tool logic works
        long assignedToolCount = sortedTools.stream().filter(t -> !t.getCurrentTechnicians().isEmpty()).count();
        logger.info("Temporary Check: Number of tools with assigned technicians: {}", assignedToolCount);

        List<Passdown> recentPassdowns = passdownRepository.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdDate"))).getContent();
        logger.info("Fetched {} recent passdowns for dashboard.", recentPassdowns.size());

        // Prepare filter dropdown data: distinct users and tools
        List<String> passdownUsers = recentPassdowns.stream()
                .map(pd -> pd.getUser().getName())
                .distinct()
                .collect(Collectors.toList());
        List<String> passdownTools = recentPassdowns.stream()
                .map(pd -> pd.getTool() != null ? pd.getTool().getName() : null)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // Prepare tool data for the grid (using the original unsorted list for mapping)
        List<Map<String, Object>> allToolsData = allTools.stream().map(tool -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", tool.getId());
            map.put("name", tool.getName());
            map.put("type", tool.getToolType() != null ? tool.getToolType().toString() : "");
            map.put("status", tool.getStatus() != null ? tool.getStatus().toString() : "");
            map.put("model", tool.getModel1());
            map.put("serial", tool.getSerialNumber1());
            map.put("location", tool.getLocation() != null ? tool.getLocation().getName() : "");
            map.put("hasAssignedUsers", tool.getCurrentTechnicians() != null && !tool.getCurrentTechnicians().isEmpty());
            return map;
        }).collect(Collectors.toList());
        
        // Fetch track trends for the filter dropdown
        List<TrackTrend> allTrackTrends = trackTrendService.getAllTrackTrends();
        logger.info("Fetched {} track/trend items for filters.", allTrackTrends.size());
        
        // Convert TrackTrend data for JavaScript, including affected tools
        List<Map<String, Object>> formattedTrackTrends = allTrackTrends.stream().map(tt -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", tt.getId());
            map.put("title", tt.getName());
            
            // Extract affected tool IDs for issue filtering
            List<Long> affectedToolIds = tt.getAffectedTools().stream()
                    .map(Tool::getId)
                    .collect(Collectors.toList());
            map.put("affectedTools", affectedToolIds);
            
            return map;
        }).collect(Collectors.toList());
        
        // Prepare tools for display with searchable notes
        List<Map<String, Object>> toolDisplayList = sortedTools.stream().map(tool -> {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("tool", tool); 
            List<Note> actualNotes = noteService.getNotesByToolId(tool.getId());
            String concatenatedNoteContent = actualNotes.stream()
                                                .map(Note::getContent)
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.joining(" \n ")); // Join with space or newline
            String toolOwnNotes = tool.getNotes() != null ? tool.getNotes() : "";
            String searchableNotes = (toolOwnNotes + " " + concatenatedNoteContent).trim();
            toolMap.put("searchableNotes", searchableNotes);
            // logger.info("Tool ID: {}, Searchable Notes: {}", tool.getId(), searchableNotes.length() > 100 ? searchableNotes.substring(0,100) + "..." : searchableNotes );
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

        return "dashboard";
    }
} 