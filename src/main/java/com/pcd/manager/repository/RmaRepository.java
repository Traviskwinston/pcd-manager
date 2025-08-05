package com.pcd.manager.repository;

import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * Returns: id, referenceNumber, status, tool.id
     */
    @Query("SELECT r.id, r.referenceNumber, r.status, r.tool.id FROM Rma r WHERE r.tool.id IN :toolIds ORDER BY r.id DESC")
    List<Object[]> findRmaListDataByToolIds(@Param("toolIds") List<Long> toolIds);
    
    /**
     * Lightweight query for RMA list view - only loads essential fields
     * Avoids loading heavy relationships that will be bulk-loaded separately
     */
    @Query("SELECT DISTINCT r FROM Rma r " +
           "LEFT JOIN FETCH r.location " +
           "ORDER BY r.writtenDate DESC NULLS LAST, r.id DESC")
    List<Rma> findAllForListView();
    
    /**
     * Ultra-lightweight query for very large lists - only essential fields
     * Returns: id, referenceNumber, status, writtenDate, location.name, tool.name
     */
    @Query("SELECT r.id, r.referenceNumber, r.status, r.writtenDate, " +
           "CASE WHEN l.name IS NOT NULL THEN l.name ELSE '' END, " +
           "CASE WHEN t.name IS NOT NULL THEN t.name ELSE '' END " +
           "FROM Rma r LEFT JOIN r.location l LEFT JOIN r.tool t " +
           "ORDER BY r.writtenDate DESC NULLS LAST, r.id DESC")
    List<Object[]> findAllUltraLightweight();
    
    /**
     * Optimized query for async list view - fetches all fields needed for the list display
     * Returns: id, referenceNumber, status, priority, customerName,
     *          writtenDate, rmaNumberProvidedDate, shippingMemoEmailedDate, partsReceivedDate,
     *          installedPartsDate, failedPartsPackedDate, failedPartsShippedDate, createdDate, updatedAt,
     *          tool.id, tool.name, location.id, location.name,
     *          problemDiscoverer, problemDiscoveryDate, whatHappened, whyAndHowItHappened, howContained, whoContained
     */
    @Query("SELECT r.id, r.referenceNumber, r.status, r.priority, r.customerName, " +
           "r.writtenDate, r.rmaNumberProvidedDate, r.shippingMemoEmailedDate, r.partsReceivedDate, " +
           "r.installedPartsDate, r.failedPartsPackedDate, r.failedPartsShippedDate, r.createdDate, r.updatedAt, " +
           "t.id, t.name, l.id, l.name, " +
           "r.problemDiscoverer, r.problemDiscoveryDate, r.whatHappened, r.whyAndHowItHappened, r.howContained, r.whoContained " +
           "FROM Rma r " +
           "LEFT JOIN r.tool t " +
           "LEFT JOIN r.location l " +
           "ORDER BY r.updatedAt DESC NULLS LAST, r.createdDate DESC NULLS LAST, r.id DESC")
    List<Object[]> findAllForAsyncListView();
    
    /**
     * Fetch part line items for specific RMA IDs
     * Returns: rmaId, partName, partNumber, productDescription
     */
    @Query("SELECT r.id, pli.partName, pli.partNumber, pli.productDescription " +
           "FROM Rma r JOIN r.partLineItems pli " +
           "WHERE r.id IN :rmaIds")
    List<Object[]> findPartLineItemsByRmaIds(@Param("rmaIds") List<Long> rmaIds);
} 