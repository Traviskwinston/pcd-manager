package com.pcd.manager.repository;

import com.pcd.manager.model.MapGridItem;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MapGridItemRepository extends JpaRepository<MapGridItem, Long> {
    
    /**
     * Find all grid items sorted by type
     */
    List<MapGridItem> findAllByOrderByTypeAsc();
    
    /**
     * Find all drawing-type grid items
     */
    List<MapGridItem> findByType(MapGridItem.ItemType type);
    
    /**
     * Find a grid item associated with a specific tool
     */
    Optional<MapGridItem> findByTool(Tool tool);
    
    /**
     * Delete any grid items associated with a specific tool
     */
    void deleteByTool(Tool tool);

    /**
     * Find all grid items for a specific location, ordered by type.
     */
    List<MapGridItem> findByLocationIdOrderByTypeAsc(Long locationId);

    /**
     * Find a grid item associated with a specific tool at a specific location.
     */
    Optional<MapGridItem> findByToolAndLocation(Tool tool, Location location);

    /**
     * Find grid items by location and type
     */
    List<MapGridItem> findByLocationIdAndType(Long locationId, MapGridItem.ItemType type);
} 