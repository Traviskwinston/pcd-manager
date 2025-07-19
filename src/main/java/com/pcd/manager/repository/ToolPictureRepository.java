package com.pcd.manager.repository;

import com.pcd.manager.model.ToolPicture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolPictureRepository extends JpaRepository<ToolPicture, Long> {
    List<ToolPicture> findByToolId(Long toolId);
} 