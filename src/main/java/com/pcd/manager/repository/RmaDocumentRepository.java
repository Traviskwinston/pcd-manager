package com.pcd.manager.repository;

import com.pcd.manager.model.RmaDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface RmaDocumentRepository extends JpaRepository<RmaDocument, Long> {
    // findById is inherited from JpaRepository

    @Query("SELECT COUNT(d) FROM RmaDocument d WHERE d.rma.id = :rmaId")
    long countByRmaId(@Param("rmaId") Long rmaId);
} 