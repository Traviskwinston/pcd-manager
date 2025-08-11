package com.pcd.manager.repository;

import com.pcd.manager.model.ToolChecklistTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ToolChecklistTemplateRepository extends JpaRepository<ToolChecklistTemplate, Long> {
    Optional<ToolChecklistTemplate> findByToolType(String toolType);
}


