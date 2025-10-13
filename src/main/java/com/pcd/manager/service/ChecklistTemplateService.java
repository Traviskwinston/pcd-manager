package com.pcd.manager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.ToolChecklistTemplate;
import com.pcd.manager.repository.ToolChecklistTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.*;

@Service
public class ChecklistTemplateService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChecklistTemplateService.class);

    private final ToolChecklistTemplateRepository templateRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ChecklistTemplateService(ToolChecklistTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public List<ChecklistItem> getChecklistForTool(Tool tool) {
        String type = tool.getToolType() != null ? tool.getToolType().name() : "SLURRY";
        // If the tool has a snapshot, use it to lock labels; otherwise use current template
        List<String> labels;
        if (tool.getChecklistLabelsJson() != null && !tool.getChecklistLabelsJson().isBlank()) {
            labels = parseItemsJson(tool.getChecklistLabelsJson());
        } else {
            labels = loadLabels(type);
        }
        List<ChecklistMapping> mappings = defaultMappings();

        // Show only the labels that exist in the template (do not pad with defaults)
        int itemCount = Math.min(labels.size(), mappings.size());
        List<ChecklistItem> result = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            ChecklistMapping map = mappings.get(i);
            String label = labels.get(i);
            LocalDate date = readDate(tool, map.dateGetter);
            // Check both date AND completion boolean flag
            Boolean completionFlag = readBoolean(tool, map.completionGetter);
            boolean completed = (completionFlag != null && completionFlag) || date != null;
            result.add(new ChecklistItem(label, date, completed));
        }
        return result;
    }

    /**
     * When a tool transitions from zero to at least one checked item, persist its current labels
     * so future global template edits do not affect this tool.
     */
    public void snapshotLabelsIfFirstCheck(Tool tool) {
        if (tool == null) return;
        boolean anyChecked = tool.getCommissionDate() != null
                || tool.getPreSl1Date() != null
                || tool.getSl1Date() != null
                || tool.getMechanicalPreSl1Date() != null
                || tool.getMechanicalPostSl1Date() != null
                || tool.getSpecificInputFunctionalityDate() != null
                || tool.getModesOfOperationDate() != null
                || tool.getSpecificSoosDate() != null
                || tool.getFieldServiceReportDate() != null
                || tool.getCertificateOfApprovalDate() != null
                || tool.getTurnedOverToCustomerDate() != null
                || tool.getStartUpSl03Date() != null
                || Boolean.TRUE.equals(tool.getCommissionComplete())
                || Boolean.TRUE.equals(tool.getPreSl1Complete())
                || Boolean.TRUE.equals(tool.getSl1Complete())
                || Boolean.TRUE.equals(tool.getMechanicalPreSl1Complete())
                || Boolean.TRUE.equals(tool.getMechanicalPostSl1Complete())
                || Boolean.TRUE.equals(tool.getSpecificInputFunctionalityComplete())
                || Boolean.TRUE.equals(tool.getModesOfOperationComplete())
                || Boolean.TRUE.equals(tool.getSpecificSoosComplete())
                || Boolean.TRUE.equals(tool.getFieldServiceReportComplete())
                || Boolean.TRUE.equals(tool.getCertificateOfApprovalComplete())
                || Boolean.TRUE.equals(tool.getTurnedOverToCustomerComplete())
                || Boolean.TRUE.equals(tool.getStartUpSl03Complete());
        if (anyChecked && (tool.getChecklistLabelsJson() == null || tool.getChecklistLabelsJson().isBlank())) {
            try {
                // Persist the labels currently displayed
                List<String> labels = loadLabels(tool.getToolType() != null ? tool.getToolType().name() : "SLURRY");
                tool.setChecklistLabelsJson(objectMapper.writeValueAsString(labels));
            } catch (Exception ignore) {}
        }
    }

    private List<String> loadLabels(String toolType) {
        return templateRepository.findByToolType(toolType)
                .map(t -> parseItemsJson(t.getItemsJson()))
                .orElseGet(this::defaultLabels);
    }

    private List<String> parseItemsJson(String json) {
        try {
            // Expect an array of strings for labels
            return objectMapper.readValue(json, new TypeReference<List<String>>(){});
        } catch (Exception e) {
            return defaultLabels();
        }
    }

    private List<String> defaultLabels() {
        return Arrays.asList(
                "Commission",
                "PreSL1",
                "SL1",
                "Mechanical: Pre SL1",
                "Mechanical: Post SL1",
                "Input Functionality Tested",
                "Operation Modes Tested",
                "SOO's Tested",
                "Field Service Report",
                "Certificate of Approval",
                "Turned Over to Customer",
                "Start-Up/SL03"
        );
    }

    private List<ChecklistMapping> defaultMappings() {
        return Arrays.asList(
                new ChecklistMapping("getCommissionDate", "getCommissionComplete", "Commission"),
                new ChecklistMapping("getPreSl1Date", "getPreSl1Complete", "PreSL1"),
                new ChecklistMapping("getSl1Date", "getSl1Complete", "SL1"),
                new ChecklistMapping("getMechanicalPreSl1Date", "getMechanicalPreSl1Complete", "Mechanical: Pre SL1"),
                new ChecklistMapping("getMechanicalPostSl1Date", "getMechanicalPostSl1Complete", "Mechanical: Post SL1"),
                new ChecklistMapping("getSpecificInputFunctionalityDate", "getSpecificInputFunctionalityComplete", "Input Functionality Tested"),
                new ChecklistMapping("getModesOfOperationDate", "getModesOfOperationComplete", "Operation Modes Tested"),
                new ChecklistMapping("getSpecificSoosDate", "getSpecificSoosComplete", "SOO's Tested"),
                new ChecklistMapping("getFieldServiceReportDate", "getFieldServiceReportComplete", "Field Service Report"),
                new ChecklistMapping("getCertificateOfApprovalDate", "getCertificateOfApprovalComplete", "Certificate of Approval"),
                new ChecklistMapping("getTurnedOverToCustomerDate", "getTurnedOverToCustomerComplete", "Turned Over to Customer"),
                new ChecklistMapping("getStartUpSl03Date", "getStartUpSl03Complete", "Start-Up/SL03")
        );
    }

    private LocalDate readDate(Tool tool, String getter) {
        try {
            Method m = Tool.class.getMethod(getter);
            Object value = m.invoke(tool);
            return (LocalDate) value;
        } catch (Exception e) {
            return null;
        }
    }
    
    private Boolean readBoolean(Tool tool, String getter) {
        if (getter == null || getter.isEmpty()) {
            return null;
        }
        try {
            Method m = Tool.class.getMethod(getter);
            Object value = m.invoke(tool);
            logger.debug("Tool {}: {} returned {}", tool.getId(), getter, value);
            return (Boolean) value;
        } catch (Exception e) {
            logger.warn("Failed to invoke {} on tool {}: {}", getter, tool.getId(), e.getMessage());
            return null;
        }
    }

    public static class ChecklistItem {
        public final String label;
        public final LocalDate date;
        public final boolean completed;

        public ChecklistItem(String label, LocalDate date, boolean completed) {
            this.label = label;
            this.date = date;
            this.completed = completed;
        }
    }

    private static class ChecklistMapping {
        public final String dateGetter;
        public final String completionGetter;
        public final String defaultLabel;

        public ChecklistMapping(String dateGetter, String completionGetter, String defaultLabel) {
            this.dateGetter = dateGetter;
            this.completionGetter = completionGetter;
            this.defaultLabel = defaultLabel;
        }
    }
}


