package com.pcd.manager.service;

import com.pcd.manager.model.ToolTypeFieldDefinition;
import com.pcd.manager.repository.ToolTypeFieldDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ToolTypeFieldDefinitionService {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolTypeFieldDefinitionService.class);
    
    private final ToolTypeFieldDefinitionRepository repository;
    
    @Autowired
    public ToolTypeFieldDefinitionService(ToolTypeFieldDefinitionRepository repository) {
        this.repository = repository;
    }
    
    public List<ToolTypeFieldDefinition> getFieldDefinitionsForToolType(String toolType) {
        logger.debug("Fetching field definitions for tool type: {}", toolType);
        return repository.findByToolTypeOrderByDisplayOrderAsc(toolType);
    }
    
    @Transactional
    public ToolTypeFieldDefinition saveFieldDefinition(ToolTypeFieldDefinition definition) {
        logger.debug("Saving field definition: toolType={}, fieldKey={}", 
                    definition.getToolType(), definition.getFieldKey());
        
        // Check if field key already exists for this tool type
        Optional<ToolTypeFieldDefinition> existing = repository.findByToolTypeAndFieldKey(
            definition.getToolType(), 
            definition.getFieldKey()
        );
        
        if (existing.isPresent() && !existing.get().getId().equals(definition.getId())) {
            throw new IllegalArgumentException(
                "Field key '" + definition.getFieldKey() + "' already exists for tool type '" + 
                definition.getToolType() + "'"
            );
        }
        
        // If display order is not set, set it to the end
        if (definition.getDisplayOrder() == null) {
            List<ToolTypeFieldDefinition> existingFields = repository.findByToolTypeOrderByDisplayOrderAsc(
                definition.getToolType()
            );
            int maxOrder = existingFields.stream()
                .mapToInt(f -> f.getDisplayOrder() != null ? f.getDisplayOrder() : 0)
                .max()
                .orElse(-1);
            definition.setDisplayOrder(maxOrder + 1);
        }
        
        return repository.save(definition);
    }
    
    @Transactional
    public void deleteFieldDefinition(Long id) {
        logger.debug("Deleting field definition with id: {}", id);
        repository.deleteById(id);
    }
    
    @Transactional
    public void deleteFieldDefinition(String toolType, String fieldKey) {
        logger.debug("Deleting field definition: toolType={}, fieldKey={}", toolType, fieldKey);
        repository.deleteByToolTypeAndFieldKey(toolType, fieldKey);
    }
    
    @Transactional
    public void reorderFieldDefinitions(String toolType, List<Long> orderedIds) {
        logger.debug("Reordering field definitions for tool type: {}", toolType);
        
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            Optional<ToolTypeFieldDefinition> definition = repository.findById(id);
            if (definition.isPresent() && definition.get().getToolType().equals(toolType)) {
                definition.get().setDisplayOrder(i);
                repository.save(definition.get());
            }
        }
    }
    
    public boolean validateFieldValue(ToolTypeFieldDefinition.FieldType fieldType, String value, String dropdownOptionsJson) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Empty values are allowed (unless required, which is checked separately)
        }
        
        switch (fieldType) {
            case NUMBER:
                try {
                    Double.parseDouble(value);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            case BOOLEAN:
                return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            case DROPDOWN:
                if (dropdownOptionsJson == null || dropdownOptionsJson.trim().isEmpty()) {
                    return false;
                }
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    List<String> options = mapper.readValue(
                        dropdownOptionsJson, 
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}
                    );
                    return options.contains(value);
                } catch (Exception e) {
                    logger.warn("Error validating dropdown value: {}", e.getMessage());
                    return false;
                }
            case TEXT:
            case DATE:
            default:
                return true;
        }
    }
}


