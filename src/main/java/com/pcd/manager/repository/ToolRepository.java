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
    Optional<Tool> findByNameIgnoreCase(String name);
    Optional<Tool> findBySerialNumber1(String serialNumber);
    Optional<Tool> findBySerialNumber2(String serialNumber2);
    
    @Query("SELECT t FROM Tool t WHERE " +
           "LOWER(t.model1) = LOWER(:model) AND " +
           "LOWER(t.name) LIKE LOWER(CONCAT('%', :namePattern, '%'))")
    List<Tool> findByModelAndNameSimilarity(@Param("model") String model, @Param("namePattern") String namePattern);
    List<Tool> findByLocationName(String locationName);
    
    // Legacy method - no longer needed with simple location field
    // @Query("SELECT t FROM Tool t JOIN t.location l WHERE l.fab = :fab AND l.state = :state")
    // List<Tool> findByFabAndState(@Param("fab") String fab, @Param("state") String state);
    
    @Query("SELECT t FROM Tool t JOIN User u WHERE u.activeTool.id = t.id AND u.id = :userId")
    Optional<Tool> findActiveToolByUserId(@Param("userId") Long userId);
    
    @Query("SELECT t FROM Tool t WHERE t.status = :status")
    List<Tool> findByStatusWithLocation(@Param("status") String status);
    
    @Query("SELECT t FROM Tool t " +
           "WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(t.serialNumber1) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(t.locationName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Tool> findBySearchTerm(@Param("searchTerm") String searchTerm);
    
    @Query(value = "SELECT t.* FROM tools t " +
           "JOIN user_tool_assignments uta ON t.id = uta.tool_id " +
           "WHERE uta.user_id = :userId", 
           nativeQuery = true)
    List<Tool> findToolsAssignedToUser(@Param("userId") Long userId);

    /**
     * Find tools by tool type
     */
    List<Tool> findByToolType(Tool.ToolType toolType);

    /**
     * Find a GasGuard tool by exact System and Equipment Location (case-insensitive)
     */
    Optional<Tool> findFirstByToolTypeAndSystemNameIgnoreCaseAndEquipmentLocationIgnoreCase(
            Tool.ToolType toolType,
            String systemName,
            String equipmentLocation
    );

    /**
     * Find GasGuard tools by Equipment Location (case-insensitive)
     */
    List<Tool> findByToolTypeAndEquipmentLocationIgnoreCase(
            Tool.ToolType toolType,
            String equipmentLocation
    );
    
    /**
     * Find all tools with their technicians eagerly loaded
     */
    @Query("SELECT DISTINCT t FROM Tool t LEFT JOIN FETCH t.currentTechnicians")
    List<Tool> findAllWithTechnicians();
    
    /**
     * Find a specific tool with its technicians eagerly loaded
     */
    @Query("SELECT t FROM Tool t LEFT JOIN FETCH t.currentTechnicians WHERE t.id = :id")
    Optional<Tool> findByIdWithTechnicians(@Param("id") Long id);
    
    /**
     * Find all tools with ALL related entities eagerly loaded to prevent N+1 queries
     * This is optimized for dashboard and list views
     */
    @Query("SELECT DISTINCT t FROM Tool t " +
           "LEFT JOIN FETCH t.currentTechnicians " +
           "LEFT JOIN FETCH t.tags")
    List<Tool> findAllWithAllRelations();
    
    /**
     * Lightweight query for tools list view - only loads essential fields
     * Avoids loading heavy relationships that will be bulk-loaded separately
     */
    @Query("SELECT DISTINCT t FROM Tool t " +
           "LEFT JOIN FETCH t.currentTechnicians")
    List<Tool> findAllForListView();
    
    /**
     * Ultra-lightweight query for tools list view - loads core tool data and checklist date fields + completion flags
     * Returns: id, name, secondaryName, toolType, serialNumber1, serialNumber2, 
     *          model1, model2, status, locationName, createdAt, updatedAt,
     *          commissionDate, preSl1Date, sl1Date, mechanicalPreSl1Date, mechanicalPostSl1Date,
     *          specificInputFunctionalityDate, modesOfOperationDate, specificSoosDate,
     *          fieldServiceReportDate, certificateOfApprovalDate, turnedOverToCustomerDate, startUpSl03Date,
     *          checklistLabelsJson, uploadDate,
     *          commissionComplete, preSl1Complete, sl1Complete, mechanicalPreSl1Complete, mechanicalPostSl1Complete,
     *          inputFunctionalityComplete, modesOperationComplete, soosComplete,
     *          fieldServiceComplete, certificateApprovalComplete, turnedOverComplete, startUpSl03Complete
     */
        @Query(value = "SELECT t.id, t.name, t.secondary_name, t.tool_type, t.serial_number1, t.serial_number2, " +
               "t.model1, t.model2, t.status, t.location_name, t.created_at, t.updated_at, " +
               "t.commission_date, t.pre_sl1date, t.sl1date, t.mechanical_pre_sl1date, t.mechanical_post_sl1date, " +
               "t.specific_input_functionality_date, t.modes_of_operation_date, t.specific_soos_date, " +
               "t.field_service_report_date, t.certificate_of_approval_date, t.turned_over_to_customer_date, t.start_up_sl03date, " +
               "t.checklist_labels_json, t.upload_date, " +
               "t.commission_complete, t.pre_sl1_complete, t.sl1_complete, t.mechanical_pre_sl1_complete, t.mechanical_post_sl1_complete, " +
               "t.specific_input_functionality_complete, t.modes_of_operation_complete, t.specific_soos_complete, " +
               "t.field_service_report_complete, t.certificate_of_approval_complete, t.turned_over_to_customer_complete, t.start_up_sl03_complete " +
               "FROM tools t " +
               "ORDER BY t.upload_date ASC NULLS FIRST, t.updated_at DESC, t.created_at DESC", nativeQuery = true)
    List<Object[]> findAllForAsyncListView();
    
    /**
     * Get technician assignments for multiple tools
     * Returns: toolId, userId, userName
     */
    @Query("SELECT t.id, u.id, u.name FROM Tool t JOIN t.currentTechnicians u WHERE t.id IN :toolIds")
    List<Object[]> findTechniciansByToolIds(@Param("toolIds") List<Long> toolIds);
    
    /**
     * Ultra-lightweight query for grid view - only loads essential fields needed for grid display
     * Returns minimal data: id, name, model1, serialNumber1, status, toolType, locationName, hasAssignedUsers
     */
    @Query("SELECT t.id, t.name, t.model1, t.serialNumber1, t.status, t.toolType, " +
           "CASE WHEN t.locationName IS NOT NULL THEN t.locationName ELSE '' END, " +
           "CASE WHEN SIZE(t.currentTechnicians) > 0 THEN true ELSE false END " +
           "FROM Tool t")
    List<Object[]> findGridViewData();
    
    /**
     * Optimized query for dashboard list view - loads only needed relationships
     * Loads tools with location and technicians but avoids heavy collections like tags
     */
    @Query("SELECT DISTINCT t FROM Tool t " +
           "LEFT JOIN FETCH t.currentTechnicians")
    List<Tool> findAllForDashboardView();
    
    /**
     * Optimized query for dashboard list view filtered by location
     * Loads tools with location and technicians but avoids heavy collections like tags
     */
    @Query("SELECT DISTINCT t FROM Tool t " +
           "LEFT JOIN FETCH t.currentTechnicians " +
           "WHERE t.locationName = :locationName")
    List<Tool> findByLocationNameForDashboardView(@Param("locationName") String locationName);

    /**
     * Get document counts for multiple tools
     * Returns: toolId, documentCount
     */
    @Query(value = "SELECT t.id, COALESCE(doc_count.count, 0) " +
           "FROM tools t " +
           "LEFT JOIN (SELECT tool_id, COUNT(*) as count FROM tool_documents GROUP BY tool_id) doc_count " +
           "ON t.id = doc_count.tool_id " +
           "WHERE t.id IN :toolIds", nativeQuery = true)
    List<Object[]> findDocumentCountsByToolIds(@Param("toolIds") List<Long> toolIds);

    /**
     * Get picture counts for multiple tools (combining legacy and new picture systems)
     * Returns: toolId, pictureCount
     */
    @Query(value = "SELECT t.id, " +
           "COALESCE(legacy_pics.count, 0) + COALESCE(new_pics.count, 0) as total_count " +
           "FROM tools t " +
           "LEFT JOIN (SELECT tool_id, COUNT(*) as count FROM tool_pictures_legacy GROUP BY tool_id) legacy_pics " +
           "ON t.id = legacy_pics.tool_id " +
           "LEFT JOIN (SELECT tool_id, COUNT(*) as count FROM tool_pictures GROUP BY tool_id) new_pics " +
           "ON t.id = new_pics.tool_id " +
           "WHERE t.id IN :toolIds", nativeQuery = true)
    List<Object[]> findPictureCountsByToolIds(@Param("toolIds") List<Long> toolIds);
} 