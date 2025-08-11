package com.pcd.manager.controller;

import com.pcd.manager.model.Tool;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import com.pcd.manager.model.ToolChecklistTemplate;
import com.pcd.manager.repository.ToolChecklistTemplateRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.service.ToolService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final ToolChecklistTemplateRepository templateRepository;
    private final ToolRepository toolRepository;
    private final ToolService toolService;

    @Autowired
    public SettingsController(ToolChecklistTemplateRepository templateRepository, ToolRepository toolRepository, ToolService toolService) {
        this.templateRepository = templateRepository;
        this.toolRepository = toolRepository;
        this.toolService = toolService;
    }

    @GetMapping
    public String settingsHome(Model model) {
        model.addAttribute("toolTypes", Tool.ToolType.values());
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
}


