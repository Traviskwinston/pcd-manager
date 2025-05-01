package com.pcd.manager.repository;

import com.pcd.manager.model.PartMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PartMovementRepository extends JpaRepository<PartMovement, Long> {
} 