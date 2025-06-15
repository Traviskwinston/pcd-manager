package com.pcd.manager.repository;

import com.pcd.manager.model.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ToolRepository extends JpaRepository<Tool, Long> {
    Optional<Tool> findByName(String name);
    Optional<Tool> findBySerialNumber1(String serialNumber);
    Optional<Tool> findBySerialNumber2(String serialNumber2);
    List<Tool> findByLocationId(Long locationId);
    
    @Query("SELECT t FROM Tool t JOIN t.location l WHERE l.fab = :fab AND l.state = :state")
    List<Tool> findByFabAndState(@Param("fab") String fab, @Param("state") String state);
    
    @Query("SELECT t FROM Tool t JOIN User u WHERE u.activeTool.id = t.id AND u.id = :userId")
    Optional<Tool> findActiveToolByUserId(@Param("userId") Long userId);
    
    @Query("SELECT t FROM Tool t JOIN FETCH t.location WHERE t.status = :status")
    List<Tool> findByStatusWithLocation(@Param("status") String status);
    
    @Query("SELECT t FROM Tool t " +
           "JOIN t.location l " +
           "WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(t.serialNumber1) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR (LOWER(l.state) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "    OR LOWER(l.fab) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Tool> findBySearchTerm(@Param("searchTerm") String searchTerm);
    
    @Query(value = "SELECT t.* FROM tools t " +
           "JOIN user_tool_assignments uta ON t.id = uta.tool_id " +
           "WHERE uta.user_id = :userId", 
           nativeQuery = true)
    List<Tool> findToolsAssignedToUser(@Param("userId") Long userId);
    
    /**
     * Find all tools with their technicians eagerly loaded
     */
    @Query("SELECT DISTINCT t FROM Tool t LEFT JOIN FETCH t.currentTechnicians")
    List<Tool> findAllWithTechnicians();
    
    /**
     * Find all tools with ALL related entities eagerly loaded to prevent N+1 queries
     * This is optimized for dashboard and list views
     */
    @Query("SELECT DISTINCT t FROM Tool t " +
           "LEFT JOIN FETCH t.currentTechnicians " +
           "LEFT JOIN FETCH t.location " +
           "LEFT JOIN FETCH t.tags")
    List<Tool> findAllWithAllRelations();
    
    /**
     * Lightweight query for tools list view - only loads essential fields
     * Avoids loading heavy relationships that will be bulk-loaded separately
     */
    @Query("SELECT DISTINCT t FROM Tool t " +
           "LEFT JOIN FETCH t.location " +
           "LEFT JOIN FETCH t.currentTechnicians")
    List<Tool> findAllForListView();
} 