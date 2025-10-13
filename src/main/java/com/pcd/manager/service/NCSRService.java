package com.pcd.manager.service;

import com.pcd.manager.model.Location;
import com.pcd.manager.model.NCSR;
import com.pcd.manager.model.Tool;
import com.pcd.manager.repository.NCSRRepository;
import com.pcd.manager.repository.ToolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NCSRService {
    
    private static final Logger logger = LoggerFactory.getLogger(NCSRService.class);
    
    @Autowired
    private NCSRRepository ncsrRepository;
    
    @Autowired
    private ToolRepository toolRepository;
    
    @Autowired
    private LocationService locationService;
    
    /**
     * Get all NCSR records
     */
    public List<NCSR> getAllNCSRs() {
        return ncsrRepository.findAll();
    }
    
    /**
     * Get NCSR by ID
     */
    public Optional<NCSR> getNCSRById(Long id) {
        return ncsrRepository.findById(id);
    }
    
    /**
     * Get all NCSR records for a specific tool
     * Matches both by direct tool assignment and by equipment number matching serial numbers
     */
    public List<NCSR> getNCSRsForTool(Long toolId) {
        Optional<Tool> toolOpt = toolRepository.findById(toolId);
        if (toolOpt.isEmpty()) {
            return Collections.emptyList();
        }
        
        Tool tool = toolOpt.get();
        String serial1 = tool.getSerialNumber1();
        String serial2 = tool.getSerialNumber2();
        
        // Get NCSRs directly assigned OR matching serial numbers
        List<NCSR> ncsrs = ncsrRepository.findByToolOrMatchingSerialNumbers(toolId, serial1, serial2);
        
        // Auto-assign NCSRs that match but aren't assigned yet
        for (NCSR ncsr : ncsrs) {
            if (ncsr.getTool() == null && matchesToolSerialNumber(ncsr, tool)) {
                logger.info("Auto-assigning NCSR {} to Tool {} based on Equipment# match", ncsr.getId(), toolId);
                ncsr.setTool(tool);
                ncsrRepository.save(ncsr);
            }
        }
        
        return ncsrs;
    }
    
    /**
     * Check if NCSR equipment number matches tool serial numbers
     */
    public boolean matchesToolSerialNumber(NCSR ncsr, Tool tool) {
        String equipNum = ncsr.getEquipmentNumber();
        if (equipNum == null || equipNum.trim().isEmpty()) {
            return false;
        }
        
        String serial1 = tool.getSerialNumber1();
        String serial2 = tool.getSerialNumber2();
        
        // Try exact match first
        if (equipNum.equals(serial1) || equipNum.equals(serial2)) {
            return true;
        }
        
        // Try case-insensitive match
        if (equipNum.equalsIgnoreCase(serial1) || equipNum.equalsIgnoreCase(serial2)) {
            return true;
        }
        
        // Try prefix match (Equipment# is the part before "-")
        if (serial1 != null && serial1.contains("-")) {
            String serial1Prefix = serial1.substring(0, serial1.indexOf("-"));
            if (equipNum.equals(serial1Prefix) || equipNum.equalsIgnoreCase(serial1Prefix)) {
                return true;
            }
        }
        
        if (serial2 != null && serial2.contains("-")) {
            String serial2Prefix = serial2.substring(0, serial2.indexOf("-"));
            if (equipNum.equals(serial2Prefix) || equipNum.equalsIgnoreCase(serial2Prefix)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Find potential tool matches for an NCSR based on equipment number
     */
    public List<Tool> findPotentialToolMatches(String equipmentNumber) {
        if (equipmentNumber == null || equipmentNumber.trim().isEmpty()) {
            logger.debug("Equipment number is null or empty");
            return Collections.emptyList();
        }
        
        // Clean and normalize equipment number
        String cleanEquipNum = equipmentNumber.trim();
        logger.info("Looking for tool matches for Equipment#: '{}' (length: {})", cleanEquipNum, cleanEquipNum.length());
        
        List<Tool> allTools = toolRepository.findAll();
        logger.info("Searching through {} tools", allTools.size());
        
        List<Tool> matches = new ArrayList<>();
        int checkedCount = 0;
        
        for (Tool tool : allTools) {
            String serial1 = tool.getSerialNumber1();
            String serial2 = tool.getSerialNumber2();
            String toolName = tool.getName();
            String secondaryName = tool.getSecondaryName();
            checkedCount++;
            
            // Log first few tools for debugging
            if (checkedCount <= 5) {
                logger.debug("Checking tool {}: name='{}', secondaryName='{}', serial1='{}', serial2='{}'", 
                    tool.getName(), toolName, secondaryName, serial1, serial2);
            }
            
            // Exact match on tool name (e.g., "BT151" matches tool name "BT151")
            if (toolName != null && cleanEquipNum.equalsIgnoreCase(toolName)) {
                logger.info("TOOL NAME MATCH FOUND! '{}' matches tool name '{}' (ID: {})", 
                    cleanEquipNum, tool.getName(), tool.getId());
                matches.add(tool);
                continue;
            }
            
            // Exact match on secondary name
            if (secondaryName != null && cleanEquipNum.equalsIgnoreCase(secondaryName)) {
                logger.info("SECONDARY NAME MATCH FOUND! '{}' matches tool secondary name '{}' on tool '{}' (ID: {})", 
                    cleanEquipNum, secondaryName, tool.getName(), tool.getId());
                matches.add(tool);
                continue;
            }
            
            // Exact match on serial numbers
            if (cleanEquipNum.equals(serial1) || cleanEquipNum.equals(serial2)) {
                logger.info("EXACT SERIAL MATCH FOUND! '{}' matches tool '{}' (serial1: '{}', serial2: '{}')", 
                    cleanEquipNum, tool.getName(), serial1, serial2);
                matches.add(tool);
                continue;
            }
            
            // Case-insensitive match on serial numbers
            if ((serial1 != null && cleanEquipNum.equalsIgnoreCase(serial1)) || 
                (serial2 != null && cleanEquipNum.equalsIgnoreCase(serial2))) {
                logger.info("CASE-INSENSITIVE SERIAL MATCH FOUND! '{}' matches tool '{}' (serial1: '{}', serial2: '{}')", 
                    cleanEquipNum, tool.getName(), serial1, serial2);
                matches.add(tool);
                continue;
            }
            
            // Prefix match
            if (serial1 != null && serial1.contains("-")) {
                String serial1Prefix = serial1.substring(0, serial1.indexOf("-"));
                if (cleanEquipNum.equals(serial1Prefix) || cleanEquipNum.equalsIgnoreCase(serial1Prefix)) {
                    logger.info("PREFIX MATCH FOUND! Equipment# '{}' matches tool '{}' serial1 prefix '{}'", 
                        cleanEquipNum, tool.getName(), serial1Prefix);
                    matches.add(tool);
                    continue;
                }
            }
            
            if (serial2 != null && serial2.contains("-")) {
                String serial2Prefix = serial2.substring(0, serial2.indexOf("-"));
                if (cleanEquipNum.equals(serial2Prefix) || cleanEquipNum.equalsIgnoreCase(serial2Prefix)) {
                    logger.info("PREFIX MATCH FOUND! Equipment# '{}' matches tool '{}' serial2 prefix '{}'", 
                        cleanEquipNum, tool.getName(), serial2Prefix);
                    matches.add(tool);
                    continue;
                }
            }
        }
        
        if (matches.isEmpty()) {
            logger.warn("NO MATCHES FOUND for Equipment#: '{}' after checking {} tools", cleanEquipNum, checkedCount);
        } else {
            logger.info("Found {} match(es) for Equipment#: '{}'", matches.size(), cleanEquipNum);
        }
        
        return matches;
    }
    
    /**
     * Save or update NCSR
     */
    @Transactional
    public NCSR saveNCSR(NCSR ncsr) {
        // If no explicit install date but marked as installed, set to today based on tool's location timezone
        if (ncsr.getInstalled() && ncsr.getInstallDate() == null) {
            ncsr.setInstallDate(getCurrentDateForToolLocation(ncsr.getTool()));
        }
        
        // Sync installed status with Open/Closed
        if (ncsr.getInstalled()) {
            ncsr.setStatus(NCSR.NcsrStatus.CLOSED);
        } else {
            ncsr.setStatus(NCSR.NcsrStatus.OPEN);
        }
        
        return ncsrRepository.save(ncsr);
    }
    
    /**
     * Toggle installed status
     */
    @Transactional
    public NCSR toggleInstalled(Long ncsrId) {
        Optional<NCSR> ncsrOpt = ncsrRepository.findById(ncsrId);
        if (ncsrOpt.isEmpty()) {
            throw new IllegalArgumentException("NCSR not found with id: " + ncsrId);
        }
        
        NCSR ncsr = ncsrOpt.get();
        boolean newInstalledStatus = !ncsr.getInstalled();
        ncsr.setInstalled(newInstalledStatus);
        
        // Sync status
        if (newInstalledStatus) {
            ncsr.setStatus(NCSR.NcsrStatus.CLOSED);
            if (ncsr.getInstallDate() == null) {
                ncsr.setInstallDate(getCurrentDateForToolLocation(ncsr.getTool()));
            }
        } else {
            ncsr.setStatus(NCSR.NcsrStatus.OPEN);
        }
        
        return ncsrRepository.save(ncsr);
    }
    
    /**
     * Update installed status and install date
     */
    @Transactional
    public NCSR updateInstalledStatus(Long ncsrId, boolean installed, LocalDate installDate) {
        Optional<NCSR> ncsrOpt = ncsrRepository.findById(ncsrId);
        if (ncsrOpt.isEmpty()) {
            throw new IllegalArgumentException("NCSR not found with id: " + ncsrId);
        }
        
        NCSR ncsr = ncsrOpt.get();
        ncsr.setInstalled(installed);
        ncsr.setInstallDate(installDate);
        
        // Sync status
        if (installed) {
            ncsr.setStatus(NCSR.NcsrStatus.CLOSED);
        } else {
            ncsr.setStatus(NCSR.NcsrStatus.OPEN);
        }
        
        return ncsrRepository.save(ncsr);
    }
    
    /**
     * Assign NCSR to a tool
     */
    @Transactional
    public NCSR assignToTool(Long ncsrId, Long toolId) {
        Optional<NCSR> ncsrOpt = ncsrRepository.findById(ncsrId);
        Optional<Tool> toolOpt = toolRepository.findById(toolId);
        
        if (ncsrOpt.isEmpty() || toolOpt.isEmpty()) {
            throw new IllegalArgumentException("NCSR or Tool not found");
        }
        
        NCSR ncsr = ncsrOpt.get();
        ncsr.setTool(toolOpt.get());
        
        return ncsrRepository.save(ncsr);
    }
    
    /**
     * Delete NCSR
     */
    @Transactional
    public void deleteNCSR(Long id) {
        ncsrRepository.deleteById(id);
    }
    
    /**
     * Get current date based on tool's location timezone
     */
    private LocalDate getCurrentDateForToolLocation(Tool tool) {
        if (tool == null) {
            return LocalDate.now(); // Default to server timezone
        }
        
        String locationName = tool.getLocationName();
        if (locationName == null || locationName.trim().isEmpty()) {
            return LocalDate.now();
        }
        
        try {
            Optional<Location> locationOpt = locationService.getLocationByName(locationName);
            if (locationOpt.isPresent() && locationOpt.get().getTimeZone() != null) {
                ZoneId zoneId = ZoneId.of(locationOpt.get().getTimeZone());
                return LocalDate.now(zoneId);
            }
        } catch (Exception e) {
            logger.warn("Error getting timezone for location {}: {}", locationName, e.getMessage());
        }
        
        return LocalDate.now();
    }
    
    /**
     * Get NCSRs by status
     */
    public List<NCSR> getNCSRsByStatus(NCSR.NcsrStatus status) {
        return ncsrRepository.findByStatus(status);
    }
    
    /**
     * Get unassigned NCSRs
     */
    public List<NCSR> getUnassignedNCSRs() {
        return ncsrRepository.findByToolIsNull();
    }
    
    /**
     * Count NCSRs for a tool
     */
    public long countNCSRsForTool(Long toolId) {
        return ncsrRepository.countByToolId(toolId);
    }
}

