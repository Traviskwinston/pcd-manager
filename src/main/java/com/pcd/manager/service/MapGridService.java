package com.pcd.manager.service;

import com.pcd.manager.model.MapGridItem;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.model.Location;
import com.pcd.manager.repository.MapGridItemRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.repository.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;

/**
 * Service for managing facility map grid items
 */
@Service
public class MapGridService {
    
    private static final Logger logger = LoggerFactory.getLogger(MapGridService.class);
    
    private final MapGridItemRepository mapGridItemRepository;
    private final ToolRepository toolRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    
    @Autowired
    public MapGridService(MapGridItemRepository mapGridItemRepository, 
                        ToolRepository toolRepository,
                        UserRepository userRepository,
                        LocationRepository locationRepository) {
        this.mapGridItemRepository = mapGridItemRepository;
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
    }
    
    /**
     * Get all grid items for a specific location
     */
    public List<MapGridItem> getGridItemsByLocationId(Long locationId) {
        if (locationId == null) {
            logger.warn("Attempted to get grid items with null locationId. Returning empty list.");
            return List.of(); // Return empty list or throw an exception
        }
        return mapGridItemRepository.findByLocationIdOrderByTypeAsc(locationId);
    }
    
    /**
     * Get a single grid item by id
     */
    public Optional<MapGridItem> getGridItemById(Long id) {
        return mapGridItemRepository.findById(id);
    }
    
    /**
     * Create a tool grid item
     */
    @Transactional
    public MapGridItem createToolGridItem(Long toolId, Integer x, Integer y, Integer width, Integer height, String userEmail, Long locationId) {
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found with ID: " + toolId));
        
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Location not found with ID: " + locationId));

        Optional<MapGridItem> existingItem = mapGridItemRepository.findByToolAndLocation(tool, location); // Check for tool at this specific location
        if (existingItem.isPresent()) {
            throw new IllegalStateException("This tool already exists on the map for this location");
        }
        
        // Find the user
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));
        
        // Create new grid item
        MapGridItem gridItem = new MapGridItem();
        gridItem.setType(MapGridItem.ItemType.TOOL);
        gridItem.setTool(tool);
        gridItem.setX(x);
        gridItem.setY(y);
        gridItem.setWidth(width);
        gridItem.setHeight(height);
        gridItem.setCreatedBy(user);
        gridItem.setUpdatedBy(user);
        gridItem.setLocation(location); // Set location
        
        return mapGridItemRepository.save(gridItem);
    }
    
    /**
     * Create a drawing grid item
     */
    @Transactional
    public MapGridItem createDrawingGridItem(Integer x, Integer y, Integer width, Integer height, 
                                            String text, String color, Boolean isSolid, String userEmail, Long locationId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));
        
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Location not found with ID: " + locationId));

        MapGridItem gridItem = new MapGridItem();
        gridItem.setType(MapGridItem.ItemType.DRAWING);
        gridItem.setX(x);
        gridItem.setY(y);
        gridItem.setWidth(width);
        gridItem.setHeight(height);
        gridItem.setText(text);
        gridItem.setColor(color);
        gridItem.setIsSolid(isSolid);
        gridItem.setCreatedBy(user);
        gridItem.setUpdatedBy(user);
        gridItem.setLocation(location); // Set location
        
        return mapGridItemRepository.save(gridItem);
    }
    
    /**
     * Update a grid item's position
     */
    @Transactional
    public MapGridItem updateGridItemPosition(Long id, Integer x, Integer y, String userEmail) {
        MapGridItem gridItem = mapGridItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Grid item not found with ID: " + id));
        
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));
        
        gridItem.setX(x);
        gridItem.setY(y);
        gridItem.setUpdatedBy(user);
        
        return mapGridItemRepository.save(gridItem);
    }
    
    /**
     * Update a drawing grid item
     */
    @Transactional
    public MapGridItem updateDrawingGridItem(Long id, Integer x, Integer y, Integer width, Integer height,
                                           String text, String color, Boolean isSolid, String userEmail) {
        MapGridItem gridItem = mapGridItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Grid item not found with ID: " + id));
        
        if (gridItem.getType() != MapGridItem.ItemType.DRAWING) {
            throw new IllegalArgumentException("Cannot update non-drawing grid item as drawing");
        }
        
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));
        
        gridItem.setX(x);
        gridItem.setY(y);
        gridItem.setWidth(width);
        gridItem.setHeight(height);
        gridItem.setText(text);
        gridItem.setColor(color);
        gridItem.setIsSolid(isSolid);
        gridItem.setUpdatedBy(user);
        
        return mapGridItemRepository.save(gridItem);
    }
    
    /**
     * Delete a grid item
     */
    @Transactional
    public void deleteGridItem(Long id) {
        mapGridItemRepository.deleteById(id);
    }
    
    /**
     * Get all available tools not yet placed on the map
     */
    public List<Tool> getAvailableTools() {
        // Get all tools
        List<Tool> allTools = toolRepository.findAll();
        
        // Get tools already on the map
        List<Tool> placedTools = mapGridItemRepository.findByType(MapGridItem.ItemType.TOOL).stream()
                .map(MapGridItem::getTool)
                .collect(Collectors.toList());
        
        // Return only tools not on the map
        return allTools.stream()
                .filter(tool -> !placedTools.contains(tool))
                .collect(Collectors.toList());
    }
    
    /**
     * Bulk update grid items (for saving map state)
     */
    @Transactional
    public void saveMapState(List<Map<String, Object>> gridItemsData, String userEmail, Long locationId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));
        
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Location not found with ID: " + locationId + " for saving map state."));
                
        List<MapGridItem> itemsToSave = new ArrayList<>();
        for (Map<String, Object> itemData : gridItemsData) {
            Long id = Long.valueOf(itemData.get("id").toString());
            MapGridItem gridItem;
            Optional<MapGridItem> existingItemOpt = mapGridItemRepository.findById(id);

            if (existingItemOpt.isPresent()) {
                gridItem = existingItemOpt.get();
                // Ensure it belongs to the correct location, though this should ideally be filtered beforehand
                if (!gridItem.getLocation().getId().equals(locationId)) {
                    logger.warn("Attempted to save map state for item ID {} which belongs to location {} but current context is location {}", id, gridItem.getLocation().getId(), locationId);
                    // Decide on handling: skip, error, or re-associate (re-associating might be dangerous)
                    continue; // Skip this item if it doesn't belong to the current location context
                }
            } else {
                // This case implies creating a new item from the saveMapState data, which is less common.
                // If new items are to be created here, ensure all necessary fields are present in itemData.
                logger.warn("MapGridItem with id {} not found. Creating new item during saveMapState is not fully supported without all fields.", id);
                // For now, we'll assume saveMapState primarily updates existing, correctly-located items.
                // If a new item is truly intended, the createToolGridItem or createDrawingGridItem should be used first.
                // However, if it's just a position update for an item that *should* exist for this location:
                gridItem = new MapGridItem(); // THIS IS RISKY IF NOT ALL DATA IS PRESENT 
                gridItem.setId(id); // This will fail if ID is auto-generated or already exists with different data
                gridItem.setLocation(location);
                // Need to set type, tool (if tool type), etc. from itemData if creating new
                // This part needs more robust handling if saveMapState is also for creation.
                // For now, focusing on updates for items already known to be at this location.
                // Let's assume for now that `id` in gridItemsData refers to an existing item for THIS location.
                // If not, `findById` would have been empty, and this block needs robust creation logic.
                 logger.error("Cannot create a new GridItem during saveMapState without full data and clear intent. Item ID: {}", id);
                 continue; // Skip if item not found, as creation here is unsafe with partial data
            }

            gridItem.setX((Integer) itemData.get("x"));
            gridItem.setY((Integer) itemData.get("y"));
            gridItem.setUpdatedBy(user);
            gridItem.setLocation(location); // Ensure location is set/re-affirmed

            if (MapGridItem.ItemType.DRAWING.equals(gridItem.getType())) {
                if (itemData.containsKey("width")) gridItem.setWidth((Integer) itemData.get("width"));
                if (itemData.containsKey("height")) gridItem.setHeight((Integer) itemData.get("height"));
                if (itemData.containsKey("text")) gridItem.setText((String) itemData.get("text"));
                if (itemData.containsKey("color")) gridItem.setColor((String) itemData.get("color"));
                if (itemData.containsKey("isSolid")) gridItem.setIsSolid((Boolean) itemData.get("isSolid"));
            } else if (MapGridItem.ItemType.TOOL.equals(gridItem.getType())) {
                // For tools, width/height might also be updatable if design allows
                 if (itemData.containsKey("width")) gridItem.setWidth((Integer) itemData.get("width"));
                if (itemData.containsKey("height")) gridItem.setHeight((Integer) itemData.get("height"));
            }
            itemsToSave.add(gridItem);
        }
        
        if (!itemsToSave.isEmpty()) {
            mapGridItemRepository.saveAll(itemsToSave);
        }
        logger.info("Map state saved for location {} with {} items by user {}", locationId, itemsToSave.size(), userEmail);
    }
} 