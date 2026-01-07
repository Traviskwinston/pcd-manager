package com.pcd.manager.repository;

import com.pcd.manager.model.ToolCustomField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ToolCustomFieldRepository extends JpaRepository<ToolCustomField, Long> {
    List<ToolCustomField> findByToolId(Long toolId);
    
    Optional<ToolCustomField> findByToolIdAndFieldKey(Long toolId, String fieldKey);
    
    void deleteByToolId(Long toolId);
    
    void deleteByToolIdAndFieldKey(Long toolId, String fieldKey);
}


