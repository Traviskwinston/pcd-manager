package com.pcd.manager.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcd.manager.model.Location;
import com.pcd.manager.model.MapGridItem;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.repository.LocationRepository;
import com.pcd.manager.repository.PassdownRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.service.MapGridService;
import com.pcd.manager.service.ToolService;
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

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @Autowired
    public DashboardController(ToolRepository toolRepository,
                             PassdownRepository passdownRepository,
                             LocationRepository locationRepository,
                             UserRepository userRepository,
                             MapGridService mapGridService,
                             ObjectMapper objectMapper,
                             ToolService toolService) {
        this.toolRepository = toolRepository;
        this.passdownRepository = passdownRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.mapGridService = mapGridService;
        this.objectMapper = objectMapper;
        this.toolService = toolService;
    }

    @GetMapping
    public String showDashboard(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
             return "redirect:/login";
        }
        String userEmail = authentication.getName(); // Email is used as username
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userEmail));

        // Fetch all tools first
        List<Tool> allTools = toolRepository.findAll();

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

        List<MapGridItem> gridItems = mapGridService.getAllGridItems();
        logger.info("Fetched {} grid items for facility map.", gridItems.size());

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
        
        // Add data needed for the dashboard template
        model.addAttribute("tools", sortedTools);
        model.addAttribute("recentPassdowns", recentPassdowns);
        model.addAttribute("locations", locationRepository.findAll());
        model.addAttribute("currentUser", user);
        model.addAttribute("gridItems", gridItems);
        model.addAttribute("allToolsData", allToolsData);

        return "dashboard";
    }
} 