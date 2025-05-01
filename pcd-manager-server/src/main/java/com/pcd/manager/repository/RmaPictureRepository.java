package com.pcd.manager.repository;

import com.pcd.manager.model.RmaPicture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RmaPictureRepository extends JpaRepository<RmaPicture, Long> {
    // findById is inherited from JpaRepository
} 