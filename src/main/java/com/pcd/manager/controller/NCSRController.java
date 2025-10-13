package com.pcd.manager.controller;

import com.pcd.manager.model.NCSR;
import com.pcd.manager.model.Tool;
import com.pcd.manager.service.NCSRExcelService;
import com.pcd.manager.service.NCSRService;
import com.pcd.manager.service.ToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/api/ncsr")
public class NCSRController {
    
    private static final Logger logger = LoggerFactory.getLogger(NCSRController.class);
    
    @Autowired
    private NCSRService ncsrService;
    
    @Autowired
    private NCSRExcelService ncsrExcelService;
    
    @Autowired
    private ToolService toolService;
    
    /**
     * Get all NCSR records for a specific tool
     */
    @GetMapping("/tool/{toolId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getNCSRsForTool(@PathVariable Long toolId) {
        try {
            List<NCSR> ncsrs = ncsrService.getNCSRsForTool(toolId);
            List<Map<String, Object>> response = new ArrayList<>();
            
            for (NCSR ncsr : ncsrs) {
                response.add(convertNCSRToMap(ncsr));
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching NCSRs for tool {}: {}", toolId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get NCSR by ID
     */
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getNCSRById(@PathVariable Long id) {
        try {
            Optional<NCSR> ncsrOpt = ncsrService.getNCSRById(id);
            if (ncsrOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(convertNCSRToMap(ncsrOpt.get()));
        } catch (Exception e) {
            logger.error("Error fetching NCSR {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Create new NCSR
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createNCSR(@RequestBody Map<String, Object> ncsrData) {
        try {
            NCSR ncsr = mapToNCSR(ncsrData, null);
            NCSR saved = ncsrService.saveNCSR(ncsr);
            
            return ResponseEntity.ok(convertNCSRToMap(saved));
        } catch (Exception e) {
            logger.error("Error creating NCSR: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Update existing NCSR
     */
    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateNCSR(@PathVariable Long id, @RequestBody Map<String, Object> ncsrData) {
        try {
            Optional<NCSR> existingOpt = ncsrService.getNCSRById(id);
            if (existingOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            NCSR ncsr = mapToNCSR(ncsrData, existingOpt.get());
            NCSR saved = ncsrService.saveNCSR(ncsr);
            
            return ResponseEntity.ok(convertNCSRToMap(saved));
        } catch (Exception e) {
            logger.error("Error updating NCSR {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Toggle installed status
     */
    @PostMapping("/{id}/toggle-installed")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleInstalled(@PathVariable Long id) {
        try {
            NCSR ncsr = ncsrService.toggleInstalled(id);
            return ResponseEntity.ok(convertNCSRToMap(ncsr));
        } catch (Exception e) {
            logger.error("Error toggling installed status for NCSR {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Update installed status and date
     */
    @PostMapping("/{id}/update-installed")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateInstalledStatus(
            @PathVariable Long id,
            @RequestParam boolean installed,
            @RequestParam(required = false) String installDate) {
        try {
            LocalDate date = null;
            if (installDate != null && !installDate.trim().isEmpty()) {
                date = LocalDate.parse(installDate);
            }
            
            NCSR ncsr = ncsrService.updateInstalledStatus(id, installed, date);
            return ResponseEntity.ok(convertNCSRToMap(ncsr));
        } catch (Exception e) {
            logger.error("Error updating installed status for NCSR {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Assign NCSR to a tool
     */
    @PostMapping("/{id}/assign-tool")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> assignToTool(@PathVariable Long id, @RequestParam Long toolId) {
        try {
            NCSR ncsr = ncsrService.assignToTool(id, toolId);
            return ResponseEntity.ok(convertNCSRToMap(ncsr));
        } catch (Exception e) {
            logger.error("Error assigning NCSR {} to tool {}: {}", id, toolId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Delete NCSR
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteNCSR(@PathVariable Long id) {
        try {
            ncsrService.deleteNCSR(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            logger.error("Error deleting NCSR {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Preview Excel import
     */
    @PostMapping("/preview-import")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> previewExcelImport(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }
            
            Map<String, Object> preview = ncsrExcelService.parseExcelForPreview(file);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            logger.error("Error previewing Excel import: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Preview NCSRs from Excel (no saving yet)
     */
    @PostMapping("/import")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> previewImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("columnMapping") String columnMappingJson) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }
            
            // Parse column mapping JSON
            Map<String, String> columnMapping = parseColumnMapping(columnMappingJson);
            
            // Preview data (don't save yet)
            Map<String, Object> importResult = ncsrExcelService.importFromExcel(file, columnMapping);
            
            // Convert NCSRs to serializable format for preview
            @SuppressWarnings("unchecked")
            List<NCSR> ncsrs = (List<NCSR>) importResult.get("ncsrs");
            List<Map<String, Object>> ncsrMaps = new ArrayList<>();
            
            for (NCSR ncsr : ncsrs) {
                ncsrMaps.add(convertNCSRToPreviewMap(ncsr));
            }
            
            importResult.put("ncsrs", ncsrMaps);
            
            return ResponseEntity.ok(importResult);
        } catch (Exception e) {
            logger.error("Error previewing import: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Finalize import - actually save NCSRs to database
     */
    @PostMapping("/finalize-import")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> finalizeImport(@RequestBody Map<String, Object> importData) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) importData.get("items");
            
            if (items == null || items.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No items provided"));
            }
            
            int savedCount = 0;
            int ignoredCount = 0;
            
            for (Map<String, Object> item : items) {
                String action = (String) item.get("action"); // "save", "ignore", "create"
                
                @SuppressWarnings("unchecked")
                Map<String, Object> ncsrMap = (Map<String, Object>) item.get("ncsr");
                
                if ("ignore".equals(action)) {
                    ignoredCount++;
                    continue;
                }
                
                // Reconstruct NCSR from map
                NCSR ncsr = reconstructNCSRFromMap(ncsrMap);
                
                if ("create".equals(action)) {
                    // Create tool first, then link NCSR
                    Tool tool = ncsrExcelService.createToolFromNCSR(ncsr);
                    ncsr.setTool(tool);
                }
                
                ncsrService.saveNCSR(ncsr);
                savedCount++;
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("saved", savedCount);
            result.put("ignored", ignoredCount);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error finalizing import: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Create tool from NCSR data
     */
    @PostMapping("/create-tool-from-ncsr")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createToolFromNCSR(@RequestParam Long ncsrId) {
        try {
            Optional<NCSR> ncsrOpt = ncsrService.getNCSRById(ncsrId);
            if (ncsrOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Tool tool = ncsrExcelService.createToolFromNCSR(ncsrOpt.get());
            
            // Assign NCSR to the new tool
            ncsrService.assignToTool(ncsrId, tool.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("toolId", tool.getId());
            response.put("toolName", tool.getName());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating tool from NCSR {}: {}", ncsrId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Find potential tool matches for an equipment number
     */
    @GetMapping("/find-tools")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> findPotentialTools(@RequestParam String equipmentNumber) {
        try {
            List<Tool> tools = ncsrService.findPotentialToolMatches(equipmentNumber);
            List<Map<String, Object>> response = new ArrayList<>();
            
            for (Tool tool : tools) {
                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("id", tool.getId());
                toolMap.put("name", tool.getName());
                toolMap.put("secondaryName", tool.getSecondaryName());
                toolMap.put("serialNumber1", tool.getSerialNumber1());
                toolMap.put("serialNumber2", tool.getSerialNumber2());
                toolMap.put("locationName", tool.getLocationName());
                response.add(toolMap);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error finding tools for equipment {}: {}", equipmentNumber, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Helper methods
    
    private Map<String, Object> convertNCSRToMap(NCSR ncsr) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", ncsr.getId());
        map.put("status", ncsr.getStatus());
        map.put("installed", ncsr.getInstalled());
        map.put("installDate", ncsr.getInstallDate());
        map.put("versumEmdQuote", ncsr.getVersumEmdQuote());
        map.put("customerLocation", ncsr.getCustomerLocation());
        map.put("customerPo", ncsr.getCustomerPo());
        map.put("customerPoReceivedDate", ncsr.getCustomerPoReceivedDate());
        map.put("supplier", ncsr.getSupplier());
        map.put("supplierPoOrProductionOrder", ncsr.getSupplierPoOrProductionOrder());
        map.put("finishDate", ncsr.getFinishDate());
        map.put("mmNumber", ncsr.getMmNumber());
        map.put("equipmentNumber", ncsr.getEquipmentNumber());
        map.put("serialNumber", ncsr.getSerialNumber());
        map.put("description", ncsr.getDescription());
        map.put("toolIdNumber", ncsr.getToolIdNumber());
        map.put("component", ncsr.getComponent());
        map.put("discrepantPartMfg", ncsr.getDiscrepantPartMfg());
        map.put("discrepantPartNumber", ncsr.getDiscrepantPartNumber());
        map.put("partLocationId", ncsr.getPartLocationId());
        map.put("partQuantity", ncsr.getPartQuantity());
        map.put("estShipDate", ncsr.getEstShipDate());
        map.put("ecrNumber", ncsr.getEcrNumber());
        map.put("contractManufacturer", ncsr.getContractManufacturer());
        map.put("trackingNumberSupplierToFse", ncsr.getTrackingNumberSupplierToFse());
        map.put("notificationToRobin", ncsr.getNotificationToRobin());
        map.put("workInstructionRequired", ncsr.getWorkInstructionRequired());
        map.put("workInstructionIdentifier", ncsr.getWorkInstructionIdentifier());
        map.put("fseFieldServiceCompletionDate", ncsr.getFseFieldServiceCompletionDate());
        map.put("toolOwner", ncsr.getToolOwner());
        map.put("comments", ncsr.getComments());
        
        if (ncsr.getTool() != null) {
            map.put("toolId", ncsr.getTool().getId());
            map.put("toolName", ncsr.getTool().getName());
        }
        
        return map;
    }
    
    private Map<String, Object> convertNCSRToPreviewMap(NCSR ncsr) {
        Map<String, Object> map = convertNCSRToMap(ncsr);
        // Add matched status
        map.put("hasMatch", ncsr.getTool() != null);
        return map;
    }
    
    private NCSR reconstructNCSRFromMap(Map<String, Object> map) {
        NCSR ncsr = new NCSR();
        
        if (map.containsKey("toolId") && map.get("toolId") != null) {
            Long toolId = ((Number) map.get("toolId")).longValue();
            Optional<Tool> toolOpt = toolService.getToolById(toolId);
            toolOpt.ifPresent(ncsr::setTool);
        }
        
        if (map.containsKey("status") && map.get("status") != null) {
            ncsr.setStatus(NCSR.NcsrStatus.valueOf(map.get("status").toString()));
        }
        if (map.containsKey("installed") && map.get("installed") != null) {
            ncsr.setInstalled((Boolean) map.get("installed"));
        }
        if (map.containsKey("installDate") && map.get("installDate") != null) {
            ncsr.setInstallDate(LocalDate.parse(map.get("installDate").toString()));
        }
        if (map.containsKey("versumEmdQuote")) {
            ncsr.setVersumEmdQuote((String) map.get("versumEmdQuote"));
        }
        if (map.containsKey("customerLocation")) {
            ncsr.setCustomerLocation((String) map.get("customerLocation"));
        }
        if (map.containsKey("customerPo")) {
            ncsr.setCustomerPo((String) map.get("customerPo"));
        }
        if (map.containsKey("customerPoReceivedDate") && map.get("customerPoReceivedDate") != null) {
            ncsr.setCustomerPoReceivedDate(LocalDate.parse(map.get("customerPoReceivedDate").toString()));
        }
        if (map.containsKey("supplier")) {
            ncsr.setSupplier((String) map.get("supplier"));
        }
        if (map.containsKey("supplierPoOrProductionOrder")) {
            ncsr.setSupplierPoOrProductionOrder((String) map.get("supplierPoOrProductionOrder"));
        }
        if (map.containsKey("finishDate") && map.get("finishDate") != null) {
            ncsr.setFinishDate(LocalDate.parse(map.get("finishDate").toString()));
        }
        if (map.containsKey("mmNumber")) {
            ncsr.setMmNumber((String) map.get("mmNumber"));
        }
        if (map.containsKey("equipmentNumber")) {
            ncsr.setEquipmentNumber((String) map.get("equipmentNumber"));
        }
        if (map.containsKey("serialNumber")) {
            ncsr.setSerialNumber((String) map.get("serialNumber"));
        }
        if (map.containsKey("description")) {
            ncsr.setDescription((String) map.get("description"));
        }
        if (map.containsKey("toolIdNumber")) {
            ncsr.setToolIdNumber((String) map.get("toolIdNumber"));
        }
        if (map.containsKey("component")) {
            ncsr.setComponent((String) map.get("component"));
        }
        if (map.containsKey("discrepantPartMfg")) {
            ncsr.setDiscrepantPartMfg((String) map.get("discrepantPartMfg"));
        }
        if (map.containsKey("discrepantPartNumber")) {
            ncsr.setDiscrepantPartNumber((String) map.get("discrepantPartNumber"));
        }
        if (map.containsKey("partLocationId")) {
            ncsr.setPartLocationId((String) map.get("partLocationId"));
        }
        if (map.containsKey("partQuantity") && map.get("partQuantity") != null) {
            ncsr.setPartQuantity(((Number) map.get("partQuantity")).intValue());
        }
        if (map.containsKey("estShipDate") && map.get("estShipDate") != null) {
            ncsr.setEstShipDate(LocalDate.parse(map.get("estShipDate").toString()));
        }
        if (map.containsKey("ecrNumber")) {
            ncsr.setEcrNumber((String) map.get("ecrNumber"));
        }
        if (map.containsKey("contractManufacturer")) {
            ncsr.setContractManufacturer((String) map.get("contractManufacturer"));
        }
        if (map.containsKey("trackingNumberSupplierToFse")) {
            ncsr.setTrackingNumberSupplierToFse((String) map.get("trackingNumberSupplierToFse"));
        }
        if (map.containsKey("notificationToRobin")) {
            ncsr.setNotificationToRobin((String) map.get("notificationToRobin"));
        }
        if (map.containsKey("workInstructionRequired") && map.get("workInstructionRequired") != null) {
            ncsr.setWorkInstructionRequired((Boolean) map.get("workInstructionRequired"));
        }
        if (map.containsKey("workInstructionIdentifier")) {
            ncsr.setWorkInstructionIdentifier((String) map.get("workInstructionIdentifier"));
        }
        if (map.containsKey("fseFieldServiceCompletionDate") && map.get("fseFieldServiceCompletionDate") != null) {
            ncsr.setFseFieldServiceCompletionDate(LocalDate.parse(map.get("fseFieldServiceCompletionDate").toString()));
        }
        if (map.containsKey("toolOwner")) {
            ncsr.setToolOwner((String) map.get("toolOwner"));
        }
        if (map.containsKey("comments")) {
            ncsr.setComments((String) map.get("comments"));
        }
        
        return ncsr;
    }
    
    private NCSR mapToNCSR(Map<String, Object> data, NCSR existing) {
        NCSR ncsr = existing != null ? existing : new NCSR();
        
        if (data.containsKey("toolId") && data.get("toolId") != null) {
            Long toolId = Long.parseLong(data.get("toolId").toString());
            Optional<Tool> toolOpt = toolService.getToolById(toolId);
            toolOpt.ifPresent(ncsr::setTool);
        }
        
        if (data.containsKey("status")) {
            ncsr.setStatus(NCSR.NcsrStatus.valueOf(data.get("status").toString()));
        }
        if (data.containsKey("installed")) {
            ncsr.setInstalled(Boolean.parseBoolean(data.get("installed").toString()));
        }
        if (data.containsKey("installDate") && data.get("installDate") != null) {
            ncsr.setInstallDate(LocalDate.parse(data.get("installDate").toString()));
        }
        if (data.containsKey("versumEmdQuote")) {
            ncsr.setVersumEmdQuote((String) data.get("versumEmdQuote"));
        }
        if (data.containsKey("customerLocation")) {
            ncsr.setCustomerLocation((String) data.get("customerLocation"));
        }
        if (data.containsKey("customerPo")) {
            ncsr.setCustomerPo((String) data.get("customerPo"));
        }
        if (data.containsKey("customerPoReceivedDate") && data.get("customerPoReceivedDate") != null) {
            ncsr.setCustomerPoReceivedDate(LocalDate.parse(data.get("customerPoReceivedDate").toString()));
        }
        if (data.containsKey("supplier")) {
            ncsr.setSupplier((String) data.get("supplier"));
        }
        if (data.containsKey("supplierPoOrProductionOrder")) {
            ncsr.setSupplierPoOrProductionOrder((String) data.get("supplierPoOrProductionOrder"));
        }
        if (data.containsKey("finishDate") && data.get("finishDate") != null) {
            ncsr.setFinishDate(LocalDate.parse(data.get("finishDate").toString()));
        }
        if (data.containsKey("mmNumber")) {
            ncsr.setMmNumber((String) data.get("mmNumber"));
        }
        if (data.containsKey("equipmentNumber")) {
            ncsr.setEquipmentNumber((String) data.get("equipmentNumber"));
        }
        if (data.containsKey("serialNumber")) {
            ncsr.setSerialNumber((String) data.get("serialNumber"));
        }
        if (data.containsKey("description")) {
            ncsr.setDescription((String) data.get("description"));
        }
        if (data.containsKey("toolIdNumber")) {
            ncsr.setToolIdNumber((String) data.get("toolIdNumber"));
        }
        if (data.containsKey("component")) {
            ncsr.setComponent((String) data.get("component"));
        }
        if (data.containsKey("discrepantPartMfg")) {
            ncsr.setDiscrepantPartMfg((String) data.get("discrepantPartMfg"));
        }
        if (data.containsKey("discrepantPartNumber")) {
            ncsr.setDiscrepantPartNumber((String) data.get("discrepantPartNumber"));
        }
        if (data.containsKey("partLocationId")) {
            ncsr.setPartLocationId((String) data.get("partLocationId"));
        }
        if (data.containsKey("partQuantity") && data.get("partQuantity") != null) {
            ncsr.setPartQuantity(Integer.parseInt(data.get("partQuantity").toString()));
        }
        if (data.containsKey("estShipDate") && data.get("estShipDate") != null) {
            ncsr.setEstShipDate(LocalDate.parse(data.get("estShipDate").toString()));
        }
        if (data.containsKey("ecrNumber")) {
            ncsr.setEcrNumber((String) data.get("ecrNumber"));
        }
        if (data.containsKey("contractManufacturer")) {
            ncsr.setContractManufacturer((String) data.get("contractManufacturer"));
        }
        if (data.containsKey("trackingNumberSupplierToFse")) {
            ncsr.setTrackingNumberSupplierToFse((String) data.get("trackingNumberSupplierToFse"));
        }
        if (data.containsKey("notificationToRobin")) {
            ncsr.setNotificationToRobin((String) data.get("notificationToRobin"));
        }
        if (data.containsKey("workInstructionRequired")) {
            ncsr.setWorkInstructionRequired(Boolean.parseBoolean(data.get("workInstructionRequired").toString()));
        }
        if (data.containsKey("workInstructionIdentifier")) {
            ncsr.setWorkInstructionIdentifier((String) data.get("workInstructionIdentifier"));
        }
        if (data.containsKey("fseFieldServiceCompletionDate") && data.get("fseFieldServiceCompletionDate") != null) {
            ncsr.setFseFieldServiceCompletionDate(LocalDate.parse(data.get("fseFieldServiceCompletionDate").toString()));
        }
        if (data.containsKey("toolOwner")) {
            ncsr.setToolOwner((String) data.get("toolOwner"));
        }
        if (data.containsKey("comments")) {
            ncsr.setComments((String) data.get("comments"));
        }
        
        return ncsr;
    }
    
    private Map<String, String> parseColumnMapping(String json) {
        // Simple JSON parsing for column mapping
        Map<String, String> mapping = new HashMap<>();
        
        json = json.trim();
        if (json.startsWith("{")) {
            json = json.substring(1);
        }
        if (json.endsWith("}")) {
            json = json.substring(0, json.length() - 1);
        }
        
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim().replace("\"", "");
                mapping.put(key, value);
            }
        }
        
        return mapping;
    }
}

