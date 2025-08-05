package com.pcd.manager.controller;

import com.pcd.manager.model.MapGridItem;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.model.Location;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.repository.LocationRepository;
import com.pcd.manager.service.MapGridService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Controller handling map grid operations
 */
@RestController
@RequestMapping("/api/map")
public class MapGridController {

    private static final Logger logger = LoggerFactory.getLogger(MapGridController.class);
    
    private final MapGridService mapGridService;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    
    @Autowired
    public MapGridController(MapGridService mapGridService, UserRepository userRepository, LocationRepository locationRepository) {
        this.mapGridService = mapGridService;
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
    }
    
    /**
     * Get all grid items
     */
    @GetMapping
    public ResponseEntity<List<MapGridItem>> getAllGridItems(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userEmail = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userEmail));
        
        Long locationId = null;
        if (currentUser.getActiveSite() != null) {
            locationId = currentUser.getActiveSite().getId();
        } else if (currentUser.getDefaultLocation() != null) {
            locationId = currentUser.getDefaultLocation().getId();
        }

        if (locationId == null) {
            logger.warn("User {} has no active or default location set. Cannot fetch grid items.", userEmail);
            return ResponseEntity.ok(List.of()); // Return empty list if no location context
        }

        List<MapGridItem> gridItems = mapGridService.getGridItemsByLocationId(locationId);
        return ResponseEntity.ok(gridItems);
    }
    
    /**
     * Get available tools for placement
     */
    @GetMapping("/available-tools")
    public ResponseEntity<Map<String, Object>> getAvailableTools(Authentication authentication) {
        // Determine the user's current location (same logic as DashboardController)
        Long currentLocationId = null;
        if (authentication != null && authentication.isAuthenticated()) {
            String userEmail = authentication.getName();
            Optional<User> userOpt = userRepository.findByEmailIgnoreCase(userEmail);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.getActiveSite() != null) {
                    currentLocationId = user.getActiveSite().getId();
                    logger.info("Using user's active site for available tools: {}", user.getActiveSite().getDisplayName());
                } else {
                    // Fall back to system default if user has no active site
                    Optional<Location> defaultLocationOpt = locationRepository.findByDefaultLocationIsTrue();
                    if (defaultLocationOpt.isPresent()) {
                        currentLocationId = defaultLocationOpt.get().getId();
                        logger.info("User has no active site, using system default for available tools: {}", defaultLocationOpt.get().getDisplayName());
                    } else {
                        logger.warn("No location available for user {}, returning tools from all locations", userEmail);
                    }
                }
            }
        }
        
        Map<String, Object> result = mapGridService.getAvailableToolsWithPlacementInfo(currentLocationId);
        @SuppressWarnings("unchecked")
        List<Tool> availableTools = (List<Tool>) result.get("tools");
        @SuppressWarnings("unchecked")
        List<Long> placedRegularIds = (List<Long>) result.get("placedRegularIds");
        @SuppressWarnings("unchecked")
        List<Long> placedFeedIds = (List<Long>) result.get("placedFeedIds");
        
        logger.info("Found {} available tools for location ID: {}", availableTools.size(), currentLocationId);
        
        // Map each Tool to a DTO with placement information
        Map<String, Object> response = new HashMap<>();
        
        List<Map<String, Object>> toolDtos = availableTools.stream().map(tool -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", tool.getId());
            dto.put("name", tool.getName());
            dto.put("type", tool.getToolType() != null ? tool.getToolType().toString() : null);
            dto.put("status", tool.getStatus() != null ? tool.getStatus().toString() : null);
            dto.put("model", tool.getModel1());
            dto.put("serial", tool.getSerialNumber1());
            dto.put("regularPlaced", placedRegularIds.contains(tool.getId()));
            dto.put("feedPlaced", placedFeedIds.contains(tool.getId()));
            return dto;
        }).collect(Collectors.toList());
        
        response.put("tools", toolDtos);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Create a tool grid item
     */
    @PostMapping("/tool")
    public ResponseEntity<MapGridItem> createToolGridItem(
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userEmail = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));

        String toolIdString = payload.get("toolId").toString();
        Integer x = (Integer) payload.get("x");
        Integer y = (Integer) payload.get("y");
        Integer width = (Integer) payload.get("width");
        Integer height = (Integer) payload.get("height");
        
        // Check if this is a Feed tool (ends with "_FEED")
        boolean isFeedTool = toolIdString.endsWith("_FEED");
        Long actualToolId;
        String feedSuffix = "";
        
        if (isFeedTool) {
            // Extract the original tool ID (remove "_FEED" suffix)
            String originalToolIdString = toolIdString.replace("_FEED", "");
            actualToolId = Long.valueOf(originalToolIdString);
            feedSuffix = "_FEED";
            logger.info("Processing Feed tool placement for original tool ID: {}", actualToolId);
        } else {
            actualToolId = Long.valueOf(toolIdString);
        }
        
        // Determine locationId: from payload or user's active/default location
        Long locationId = payload.containsKey("locationId") ? Long.valueOf(payload.get("locationId").toString()) : null;
        if (locationId == null) {
            if (currentUser.getActiveSite() != null) {
                locationId = currentUser.getActiveSite().getId();
            } else if (currentUser.getDefaultLocation() != null) {
                locationId = currentUser.getDefaultLocation().getId();
            } else {
                logger.error("Cannot create tool grid item: No locationId in payload and user {} has no active/default location.", userEmail);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); // Or a proper error response
            }
        }
        
        MapGridItem gridItem = mapGridService.createToolGridItem(actualToolId, x, y, width, height, userEmail, locationId, isFeedTool);
        logger.info("Created {} grid item with ID: {}", isFeedTool ? "feed tool" : "tool", gridItem.getId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(gridItem);
    }
    
    /**
     * Create a drawing grid item
     */
    @PostMapping("/drawing")
    public ResponseEntity<MapGridItem> createDrawingGridItem(
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userEmail = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));

        Integer x = (Integer) payload.get("x");
        Integer y = (Integer) payload.get("y");
        Integer width = (Integer) payload.get("width");
        Integer height = (Integer) payload.get("height");
        String text = (String) payload.get("text");
        String color = (String) payload.get("color");
        Boolean isSolid = (Boolean) payload.get("isSolid");
        
        // Determine locationId
        Long locationId = payload.containsKey("locationId") ? Long.valueOf(payload.get("locationId").toString()) : null;
        if (locationId == null) {
            if (currentUser.getActiveSite() != null) {
                locationId = currentUser.getActiveSite().getId();
            } else if (currentUser.getDefaultLocation() != null) {
                locationId = currentUser.getDefaultLocation().getId();
            } else {
                logger.error("Cannot create drawing grid item: No locationId in payload and user {} has no active/default location.", userEmail);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }
        }

        MapGridItem gridItem = mapGridService.createDrawingGridItem(
                x, y, width, height, text, color, isSolid, userEmail, locationId);
        logger.info("Created drawing grid item with ID: {}", gridItem.getId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(gridItem);
    }
    
    /**
     * Update a grid item's position
     */
    @PutMapping("/{id}/position")
    public ResponseEntity<MapGridItem> updateGridItemPosition(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userEmail = authentication.getName();
        
        Integer x = (Integer) payload.get("x");
        Integer y = (Integer) payload.get("y");
        
        try {
            MapGridItem updatedItem = mapGridService.updateGridItemPosition(id, x, y, userEmail);
            return ResponseEntity.ok(updatedItem);
        } catch (IllegalArgumentException e) {
            logger.error("Error updating grid item position: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Update a drawing grid item
     */
    @PutMapping("/drawing/{id}")
    public ResponseEntity<MapGridItem> updateDrawingGridItem(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userEmail = authentication.getName();
        
        Integer x = (Integer) payload.get("x");
        Integer y = (Integer) payload.get("y");
        Integer width = (Integer) payload.get("width");
        Integer height = (Integer) payload.get("height");
        String text = (String) payload.get("text");
        String color = (String) payload.get("color");
        Boolean isSolid = (Boolean) payload.get("isSolid");
        
        try {
            MapGridItem updatedItem = mapGridService.updateDrawingGridItem(
                    id, x, y, width, height, text, color, isSolid, userEmail);
            return ResponseEntity.ok(updatedItem);
        } catch (IllegalArgumentException e) {
            logger.error("Error updating drawing grid item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }
    
    /**
     * Delete a grid item
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGridItem(@PathVariable Long id) {
        try {
            mapGridService.deleteGridItem(id);
            logger.info("Deleted grid item with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting grid item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Save the entire map state
     */
    @PostMapping("/save")
    public ResponseEntity<Void> saveMapState(
            @RequestBody List<Map<String, Object>> gridItems,
            @RequestParam(required = false) Long locationId,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userEmail = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userEmail));

        Long effectiveLocationId = locationId;
        if (effectiveLocationId == null) {
            if (currentUser.getActiveSite() != null) {
                effectiveLocationId = currentUser.getActiveSite().getId();
            } else if (currentUser.getDefaultLocation() != null) {
                effectiveLocationId = currentUser.getDefaultLocation().getId();
            } else {
                logger.error("Cannot save map state: No locationId provided and user {} has no active/default location.", userEmail);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }
        
        try {
            mapGridService.saveMapState(gridItems, userEmail, effectiveLocationId);
            logger.info("Map state saved with {} items", gridItems.size());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error saving map state: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
} 