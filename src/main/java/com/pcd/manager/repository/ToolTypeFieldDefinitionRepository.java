package com.pcd.manager.repository;

import com.pcd.manager.model.ToolTypeFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ToolTypeFieldDefinitionRepository extends JpaRepository<ToolTypeFieldDefinition, Long> {
    List<ToolTypeFieldDefinition> findByToolTypeOrderByDisplayOrderAsc(String toolType);
    
    Optional<ToolTypeFieldDefinition> findByToolTypeAndFieldKey(String toolType, String fieldKey);
    
    void deleteByToolTypeAndFieldKey(String toolType, String fieldKey);
}


