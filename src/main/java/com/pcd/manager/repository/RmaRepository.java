package com.pcd.manager.repository;

import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RmaRepository extends JpaRepository<Rma, Long> {
    
    List<Rma> findByStatus(RmaStatus status);
    
    List<Rma> findByCustomerNameContainingIgnoreCase(String customerName);
    
    @Query("SELECT r FROM Rma r ORDER BY " +
           "CASE r.status " +
           "    WHEN 'IN_PROGRESS' THEN 1 " +
           "    WHEN 'RECEIVED' THEN 2 " +
           "    WHEN 'COMPLETED' THEN 3 " +
           "    WHEN 'SHIPPED' THEN 4 " +
           "    ELSE 5 " +
           "END, " +
           "r.priority DESC, r.receivedDate ASC")
    List<Rma> findAllOrderedByStatusAndPriority();
    
    @Query("SELECT DISTINCT r FROM Rma r " +
           "LEFT JOIN FETCH r.tool " +
           "LEFT JOIN FETCH r.location " +
           "ORDER BY r.writtenDate DESC NULLS LAST, r.id DESC")
    List<Rma> findAllOrderedByWrittenDateDesc();
    
    List<Rma> findTop5ByOrderByCreatedDateDesc();
    
    /**
     * Find all RMAs associated with a specific tool
     * 
     * @param toolId the ID of the tool
     * @return list of RMAs associated with the tool
     */
    List<Rma> findByToolId(Long toolId);
    
    /**
     * Find all RMAs associated with any of the specified tool IDs
     * 
     * @param toolIds the IDs of the tools
     * @return list of RMAs associated with any of the tools
     */
    List<Rma> findByToolIdIn(List<Long> toolIds);
    
    /**
     * Lightweight query for tools list view - only loads essential RMA fields
     * Returns: id, rmaNumber, status, tool.id
     */
    @Query("SELECT r.id, r.rmaNumber, r.status, r.tool.id FROM Rma r WHERE r.tool.id IN :toolIds ORDER BY r.id DESC")
    List<Object[]> findRmaListDataByToolIds(List<Long> toolIds);
} 