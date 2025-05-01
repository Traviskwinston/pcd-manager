package com.pcd.manager.repository;

import com.pcd.manager.model.RmaDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RmaDocumentRepository extends JpaRepository<RmaDocument, Long> {
    // findById is inherited from JpaRepository
} 