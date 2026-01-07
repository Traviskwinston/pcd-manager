package com.pcd.manager.controller;

import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.service.UserService;
import com.pcd.manager.service.ExcelCheckboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import com.pcd.manager.model.ToolChecklistTemplate;
import com.pcd.manager.model.ToolTypeFieldDefinition;
import com.pcd.manager.repository.ToolChecklistTemplateRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.ToolTypeFieldDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/settings")
public class SettingsController {
    
    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    private final ToolChecklistTemplateRepository templateRepository;
    private final ToolRepository toolRepository;
    private final ToolService toolService;
    private final UserService userService;
    private final ExcelCheckboxService excelCheckboxService;
    private final ToolTypeFieldDefinitionService fieldDefinitionService;
    
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public SettingsController(ToolChecklistTemplateRepository templateRepository, 
                              ToolRepository toolRepository, 
                              ToolService toolService,
                              UserService userService,
                              ExcelCheckboxService excelCheckboxService,
                              ToolTypeFieldDefinitionService fieldDefinitionService) {
        this.templateRepository = templateRepository;
        this.excelCheckboxService = excelCheckboxService;
        this.toolRepository = toolRepository;
        this.toolService = toolService;
        this.userService = userService;
        this.fieldDefinitionService = fieldDefinitionService;
    }

    @GetMapping
    public String settingsHome(Model model) {
        model.addAttribute("toolTypes", Tool.ToolType.values());
        
        // Add current user for admin check
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            userService.getUserByEmail(auth.getName()).ifPresent(user -> {
                model.addAttribute("currentUser", user);
            });
        }
        
        return "settings/index";
    }

    // Placeholder in-memory, replace with repository/service later
    @PostMapping("/checklist/{toolType}")
    public ResponseEntity<?> saveChecklist(@PathVariable String toolType, @RequestBody String itemsJson) {
        // Validate basic input
        if (itemsJson == null || itemsJson.isBlank()) {
            return ResponseEntity.badRequest().body("Checklist items cannot be empty");
        }
        ToolChecklistTemplate template = templateRepository.findByToolType(toolType)
                .orElseGet(() -> {
                    ToolChecklistTemplate t = new ToolChecklistTemplate();
                    t.setToolType(toolType);
                    return t;
                });
        template.setItemsJson(itemsJson);
        templateRepository.save(template);

        // If items were removed, clear corresponding dates on tools of this type
        // We compare default mapping order and incoming labels; any trailing removed items will be cleared.
        // IMPORTANT: Tools that already have at least one checklist item checked are NOT affected.
        List<String> labels;
        try {
            labels = new com.fasterxml.jackson.databind.ObjectMapper().readValue(itemsJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>(){});
        } catch (Exception e) {
            labels = java.util.Collections.emptyList();
        }
        // Default mapping order (must match ChecklistTemplateService)
        String[] getters = new String[]{
            "getCommissionDate", "getPreSl1Date", "getSl1Date", "getMechanicalPreSl1Date", "getMechanicalPostSl1Date",
            "getSpecificInputFunctionalityDate", "getModesOfOperationDate", "getSpecificSoosDate", "getFieldServiceReportDate",
            "getCertificateOfApprovalDate", "getTurnedOverToCustomerDate", "getStartUpSl03Date"
        };
        try {
            Tool.ToolType tt = Tool.ToolType.valueOf(toolType);
            // Load tools of this type once
            java.util.List<Tool> allToolsOfType = toolRepository.findByToolType(tt);
            // Filter to only tools with NO checklist items checked
            java.util.List<Tool> toolsWithoutAnyChecked = new java.util.ArrayList<>();
            for (Tool t : allToolsOfType) {
                if (!hasAnyChecklistItemChecked(t)) {
                    toolsWithoutAnyChecked.add(t);
                }
            }
            // Clear trailing removed items only for unaffected tools
            for (int i = labels.size(); i < getters.length; i++) {
                toolService.clearChecklistDateForTools(toolsWithoutAnyChecked, getters[i]);
            }
        } catch (IllegalArgumentException ignore) { }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/checklist/{toolType}")
    public ResponseEntity<?> getChecklist(@PathVariable String toolType) {
        return templateRepository.findByToolType(toolType)
                .map(t -> ResponseEntity.ok(t.getItemsJson()))
                .orElseGet(() -> ResponseEntity.ok("[]"));
    }

    // Helpers
    private boolean hasAnyChecklistItemChecked(Tool t) {
        return t.getCommissionDate() != null
                || t.getPreSl1Date() != null
                || t.getSl1Date() != null
                || t.getMechanicalPreSl1Date() != null
                || t.getMechanicalPostSl1Date() != null
                || t.getSpecificInputFunctionalityDate() != null
                || t.getModesOfOperationDate() != null
                || t.getSpecificSoosDate() != null
                || t.getFieldServiceReportDate() != null
                || t.getCertificateOfApprovalDate() != null
                || t.getTurnedOverToCustomerDate() != null
                || t.getStartUpSl03Date() != null;
    }
    
    /**
     * DANGER ZONE: Clear all data from the database (keeps table structure)
     * Admin only endpoint with two-step confirmation required from frontend
     */
    @PostMapping("/admin/clear-database")
    @Transactional
    public ResponseEntity<?> clearDatabase() {
        try {
            // Verify admin privileges
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                logger.warn("Unauthorized attempt to clear database - not authenticated");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
            }
            
            User currentUser = userService.getUserByEmail(auth.getName()).orElse(null);
            if (currentUser == null || !"ADMIN".equals(currentUser.getRole())) {
                logger.warn("Unauthorized attempt to clear database by user: {}", auth.getName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin privileges required");
            }
            
            logger.warn("ADMIN {} initiated database clear operation", currentUser.getEmail());
            
            // Disable foreign key checks temporarily
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
            
            // Truncate all tables (keeps structure, deletes data)
            String[] tables = {
                "tool_technicians",
                "tool_tags",
                "tool_documents",
                "tool_document_names",
                "tool_document_tags",
                "tool_pictures_legacy",
                "tool_picture_names_legacy",
                "tool_comments",
                "tool_pictures",
                "moving_parts",
                "part_movements",
                "map_grid_items",
                "passdown_pictures",
                "passdown",
                "rma_comments",
                "rma_documents",
                "rma_pictures",
                "rma_parts",
                "rma",
                "track_trend_comments",
                "track_trend_pictures",
                "track_trend_tools",
                "track_trend_rmas",
                "track_trend",
                "ncsr",
                "notes",
                "labor_entries",
                "tools",
                "parts",
                "tool_checklist_templates",
                "locations",
                "projects"
            };
            
            int clearedCount = 0;
            for (String table : tables) {
                try {
                    // Check if table exists before truncating
                    int result = entityManager.createNativeQuery("TRUNCATE TABLE " + table + " RESTART IDENTITY").executeUpdate();
                    logger.info("Cleared table: {}", table);
                    clearedCount++;
                } catch (Exception e) {
                    logger.warn("Failed to clear table {}: {}", table, e.getMessage());
                }
            }
            
            // Clear all users EXCEPT the current admin
            try {
                int deletedUsers = entityManager.createNativeQuery(
                    "DELETE FROM users WHERE email != :adminEmail"
                ).setParameter("adminEmail", currentUser.getEmail()).executeUpdate();
                logger.info("Deleted {} users (preserved admin: {})", deletedUsers, currentUser.getEmail());
            } catch (Exception e) {
                logger.warn("Failed to clear users table: {}", e.getMessage());
            }
            
            // Re-enable foreign key checks
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
            
            logger.warn("DATABASE CLEARED by admin user: {} - {} tables cleared", currentUser.getEmail(), clearedCount);
            
            return ResponseEntity.ok().body("Database cleared successfully. " + clearedCount + " tables cleared.");
            
        } catch (Exception e) {
            logger.error("Error clearing database", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error clearing database: " + e.getMessage());
        }
    }
    
    /**
     * Analyzes an uploaded Excel file to identify all checkboxes and their states
     * Used to understand the checkbox structure in uploaded RMA files
     */
    @PostMapping("/analyze-rma-checkboxes")
    public ResponseEntity<?> analyzeRmaCheckboxes(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Please select a file to upload");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            logger.info("Analyzing checkboxes in uploaded file: {} ({} bytes)", 
                       file.getOriginalFilename(), file.getSize());
            
            // Save file temporarily
            String tempDir = System.getProperty("java.io.tmpdir");
            String tempFileName = "checkbox-analysis-" + System.currentTimeMillis() + ".xlsx";
            java.nio.file.Path tempFile = java.nio.file.Paths.get(tempDir, tempFileName);
            
            try {
                file.transferTo(tempFile.toFile());
                logger.info("Saved temporary file: {}", tempFile);
                
                // Analyze the file
                Map<String, Object> analysis = excelCheckboxService.analyzeCheckboxes(tempFile.toString());
                
                return ResponseEntity.ok(analysis);
                
            } finally {
                // Clean up temp file
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                    logger.info("Deleted temporary file: {}", tempFile);
                } catch (Exception e) {
                    logger.warn("Could not delete temp file: {}", tempFile, e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error analyzing Excel checkboxes", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get field definitions for a tool type
     */
    @GetMapping("/tool-fields/{toolType}")
    public ResponseEntity<?> getFieldDefinitions(@PathVariable String toolType) {
        try {
            List<ToolTypeFieldDefinition> definitions = fieldDefinitionService.getFieldDefinitionsForToolType(toolType);
            return ResponseEntity.ok(definitions);
        } catch (Exception e) {
            logger.error("Error fetching field definitions for tool type: {}", toolType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching field definitions: " + e.getMessage());
        }
    }
    
    /**
     * Save field definition (create or update)
     */
    @PostMapping("/tool-fields/{toolType}")
    public ResponseEntity<?> saveFieldDefinition(@PathVariable String toolType, @RequestBody Map<String, Object> requestData) {
        try {
            ToolTypeFieldDefinition definition = new ToolTypeFieldDefinition();
            
            // Set ID if provided (for updates)
            if (requestData.containsKey("id") && requestData.get("id") != null) {
                definition.setId(Long.valueOf(requestData.get("id").toString()));
            }
            
            // Ensure tool type matches path
            definition.setToolType(toolType);
            
            // Set basic fields
            if (requestData.containsKey("fieldKey")) {
                definition.setFieldKey(requestData.get("fieldKey").toString());
            }
            if (requestData.containsKey("fieldLabel")) {
                definition.setFieldLabel(requestData.get("fieldLabel").toString());
            }
            if (requestData.containsKey("fieldType")) {
                try {
                    definition.setFieldType(ToolTypeFieldDefinition.FieldType.valueOf(requestData.get("fieldType").toString()));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body("Invalid field type");
                }
            }
            if (requestData.containsKey("isRequired")) {
                definition.setIsRequired(Boolean.valueOf(requestData.get("isRequired").toString()));
            }
            if (requestData.containsKey("displayOrder")) {
                definition.setDisplayOrder(Integer.valueOf(requestData.get("displayOrder").toString()));
            }
            
            // Handle dropdown options
            if (requestData.containsKey("dropdownOptions") && definition.getFieldType() == ToolTypeFieldDefinition.FieldType.DROPDOWN) {
                @SuppressWarnings("unchecked")
                List<String> options = (List<String>) requestData.get("dropdownOptions");
                definition.setDropdownOptions(options);
            }
            
            // Validate field key format (alphanumeric and underscores only)
            if (definition.getFieldKey() != null && !definition.getFieldKey().matches("^[a-zA-Z0-9_]+$")) {
                return ResponseEntity.badRequest()
                        .body("Field key must contain only letters, numbers, and underscores");
            }
            
            ToolTypeFieldDefinition saved = fieldDefinitionService.saveFieldDefinition(definition);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error saving field definition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving field definition: " + e.getMessage());
        }
    }
    
    /**
     * Delete a field definition
     */
    @DeleteMapping("/tool-fields/{toolType}/{fieldKey}")
    public ResponseEntity<?> deleteFieldDefinition(@PathVariable String toolType, @PathVariable String fieldKey) {
        try {
            fieldDefinitionService.deleteFieldDefinition(toolType, fieldKey);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error deleting field definition: toolType={}, fieldKey={}", toolType, fieldKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting field definition: " + e.getMessage());
        }
    }
    
    /**
     * Reorder field definitions
     */
    @PostMapping("/tool-fields/{toolType}/reorder")
    public ResponseEntity<?> reorderFieldDefinitions(@PathVariable String toolType, @RequestBody List<Long> orderedIds) {
        try {
            fieldDefinitionService.reorderFieldDefinitions(toolType, orderedIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error reordering field definitions for toolType: {}", toolType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error reordering field definitions: " + e.getMessage());
        }
    }
}


