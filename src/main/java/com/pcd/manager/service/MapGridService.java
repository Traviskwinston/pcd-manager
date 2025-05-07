package com.pcd.manager.service;

import com.pcd.manager.model.MapGridItem;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.repository.MapGridItemRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing facility map grid items
 */
@Service
public class MapGridService {
    
    private static final Logger logger = LoggerFactory.getLogger(MapGridService.class);
    
    private final MapGridItemRepository mapGridItemRepository;
    private final ToolRepository toolRepository;
    private final UserRepository userRepository;
    
    @Autowired
    public MapGridService(MapGridItemRepository mapGridItemRepository, 
                        ToolRepository toolRepository,
                        UserRepository userRepository) {
        this.mapGridItemRepository = mapGridItemRepository;
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }
    
    /**
     * Get all grid items
     */
    public List<MapGridItem> getAllGridItems() {
        return mapGridItemRepository.findAllByOrderByTypeAsc();
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
    public MapGridItem createToolGridItem(Long toolId, Integer x, Integer y, Integer width, Integer height, String userEmail) {
        // Find the tool
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found with ID: " + toolId));
        
        // Check if this tool already has a grid item
        Optional<MapGridItem> existingItem = mapGridItemRepository.findByTool(tool);
        if (existingItem.isPresent()) {
            throw new IllegalStateException("This tool already exists on the map");
        }
        
        // Find the user
        User user = userRepository.findByEmail(userEmail)
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
        
        return mapGridItemRepository.save(gridItem);
    }
    
    /**
     * Create a drawing grid item
     */
    @Transactional
    public MapGridItem createDrawingGridItem(Integer x, Integer y, Integer width, Integer height, 
                                            String text, String color, Boolean isSolid, String userEmail) {
        // Find the user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));
        
        // Create new grid item
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
        
        return mapGridItemRepository.save(gridItem);
    }
    
    /**
     * Update a grid item's position
     */
    @Transactional
    public MapGridItem updateGridItemPosition(Long id, Integer x, Integer y, String userEmail) {
        MapGridItem gridItem = mapGridItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Grid item not found with ID: " + id));
        
        User user = userRepository.findByEmail(userEmail)
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
        
        User user = userRepository.findByEmail(userEmail)
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
    public void saveMapState(List<Map<String, Object>> gridItems, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));
                
        for (Map<String, Object> item : gridItems) {
            Long id = Long.valueOf(item.get("id").toString());
            Integer x = (Integer) item.get("x");
            Integer y = (Integer) item.get("y");
            
            // For drawings, we might also update width and height
            if (item.containsKey("width") && item.containsKey("height")) {
                Integer width = (Integer) item.get("width");
                Integer height = (Integer) item.get("height");
                
                Optional<MapGridItem> gridItemOpt = mapGridItemRepository.findById(id);
                if (gridItemOpt.isPresent()) {
                    MapGridItem gridItem = gridItemOpt.get();
                    gridItem.setX(x);
                    gridItem.setY(y);
                    gridItem.setWidth(width);
                    gridItem.setHeight(height);
                    gridItem.setUpdatedBy(user);
                    
                    mapGridItemRepository.save(gridItem);
                }
            } else {
                // Just update position
                updateGridItemPosition(id, x, y, userEmail);
            }
        }
        
        logger.info("Map state saved with {} items by user {}", gridItems.size(), userEmail);
    }
} 