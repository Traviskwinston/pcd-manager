package com.pcd.manager.repository;

import com.pcd.manager.model.NCSR;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NCSRRepository extends JpaRepository<NCSR, Long> {
} 