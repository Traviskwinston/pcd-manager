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
    
    List<Rma> findTop5ByOrderByCreatedDateDesc();
} 