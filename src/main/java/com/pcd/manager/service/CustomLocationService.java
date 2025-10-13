package com.pcd.manager.service;

import com.pcd.manager.model.CustomLocation;
import com.pcd.manager.model.Location;
import com.pcd.manager.model.MovingPart;
import com.pcd.manager.repository.CustomLocationRepository;
import com.pcd.manager.repository.MovingPartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CustomLocationService {

    private static final Logger logger = LoggerFactory.getLogger(CustomLocationService.class);

    @Autowired
    private CustomLocationRepository customLocationRepository;

    @Autowired
    private MovingPartRepository movingPartRepository;

    /**
     * Get all custom locations for a specific location
     */
    public List<CustomLocation> getCustomLocationsByLocation(Location location) {
        return customLocationRepository.findByLocationOrderByNameAsc(location);
    }

    /**
     * Get custom locations with part counts for display
     */
    public Map<CustomLocation, Integer> getCustomLocationsWithPartCounts(Location location) {
        List<Object[]> results = customLocationRepository.findByLocationWithPartCounts(location);
        Map<CustomLocation, Integer> map = new LinkedHashMap<>();
        
        for (Object[] result : results) {
            CustomLocation cl = (CustomLocation) result[0];
            Long count = (Long) result[1];
            map.put(cl, count.intValue());
        }
        
        return map;
    }

    /**
     * Get a custom location by ID
     */
    public Optional<CustomLocation> getCustomLocationById(Long id) {
        return customLocationRepository.findById(id);
    }

    /**
     * Find a custom location by name and location
     */
    public Optional<CustomLocation> findByNameAndLocation(String name, Location location) {
        return customLocationRepository.findByNameIgnoreCaseAndLocation(name, location);
    }

    /**
     * Find or create a custom location by name
     * This is used when importing from text-based custom locations
     */
    @Transactional
    public CustomLocation findOrCreateCustomLocation(String name, Location location) {
        Optional<CustomLocation> existing = customLocationRepository.findByNameIgnoreCaseAndLocation(name, location);
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Create new custom location
        CustomLocation newLocation = new CustomLocation();
        newLocation.setName(name);
        newLocation.setLocation(location);
        newLocation.setDescription("Auto-created custom location");
        
        return customLocationRepository.save(newLocation);
    }

    /**
     * Create a new custom location
     */
    @Transactional
    public CustomLocation createCustomLocation(CustomLocation customLocation) {
        return customLocationRepository.save(customLocation);
    }

    /**
     * Update an existing custom location
     */
    @Transactional
    public CustomLocation updateCustomLocation(Long id, CustomLocation updatedLocation) {
        CustomLocation existing = customLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Custom location not found"));
        
        existing.setName(updatedLocation.getName());
        existing.setDescription(updatedLocation.getDescription());
        
        return customLocationRepository.save(existing);
    }

    /**
     * Update custom location AND update all MovingPart references that use the old name
     */
    @Transactional
    public CustomLocation updateCustomLocationAndReferences(Long id, CustomLocation updatedLocation, String oldName) {
        // First, update the custom location itself
        CustomLocation updatedCustomLocation = updateCustomLocation(id, updatedLocation);
        
        // If the name changed, update all MovingParts that reference the old name
        if (!oldName.equals(updatedLocation.getName())) {
            String newName = updatedLocation.getName();
            logger.info("Updating MovingPart references from '{}' to '{}'", oldName, newName);
            
            // Get ALL moving parts
            List<MovingPart> allMovingParts = movingPartRepository.findAll();
            
            for (MovingPart mp : allMovingParts) {
                boolean wasUpdated = false;
                
                // Update fromCustomLocation if it matches the old name
                if (mp.getFromCustomLocation() != null && mp.getFromCustomLocation().equalsIgnoreCase(oldName)) {
                    mp.setFromCustomLocation(newName);
                    wasUpdated = true;
                    logger.info("Updated MovingPart {} fromCustomLocation: {} -> {}", mp.getId(), oldName, newName);
                }
                
                // Update toCustomLocations list if it contains the old name
                List<String> toLocations = mp.getToCustomLocationsList();
                if (toLocations != null && !toLocations.isEmpty()) {
                    boolean listUpdated = false;
                    for (int i = 0; i < toLocations.size(); i++) {
                        if (toLocations.get(i).equalsIgnoreCase(oldName)) {
                            toLocations.set(i, newName);
                            listUpdated = true;
                        }
                    }
                    if (listUpdated) {
                        mp.setToCustomLocationsList(toLocations);
                        wasUpdated = true;
                        logger.info("Updated MovingPart {} toCustomLocationsList to include: {}", mp.getId(), newName);
                    }
                }
                
                if (wasUpdated) {
                    movingPartRepository.save(mp);
                }
            }
        }
        
        return updatedCustomLocation;
    }

    /**
     * Delete a custom location
     */
    @Transactional
    public void deleteCustomLocation(Long id) {
        customLocationRepository.deleteById(id);
    }

    /**
     * Get all moving parts for a custom location (incoming and outgoing)
     * This includes both entity-linked moving parts AND text-based custom locations
     */
    public Map<String, List<MovingPart>> getMovingPartsForCustomLocation(Long customLocationId) {
        CustomLocation customLocation = customLocationRepository.findById(customLocationId)
                .orElseThrow(() -> new RuntimeException("Custom location not found"));
        
        String locationName = customLocation.getName();
        
        // Get all moving parts from the repository
        List<MovingPart> allMovingParts = movingPartRepository.findAll();
        
        // Find incoming parts (where this location is the destination)
        List<MovingPart> incoming = allMovingParts.stream()
                .filter(mp -> {
                    // Check entity reference
                    if (mp.getToCustomLocationEntity() != null && 
                        mp.getToCustomLocationEntity().getId().equals(customLocationId)) {
                        return true;
                    }
                    // Check text-based custom locations
                    if (mp.getToCustomLocationsList() != null && 
                        mp.getToCustomLocationsList().stream()
                            .anyMatch(loc -> loc.equalsIgnoreCase(locationName))) {
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
        
        // Find outgoing parts (where this location is the source)
        List<MovingPart> outgoing = allMovingParts.stream()
                .filter(mp -> {
                    // Check entity reference
                    if (mp.getFromCustomLocationEntity() != null && 
                        mp.getFromCustomLocationEntity().getId().equals(customLocationId)) {
                        return true;
                    }
                    // Check text-based custom location
                    if (mp.getFromCustomLocation() != null && 
                        mp.getFromCustomLocation().equalsIgnoreCase(locationName)) {
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
        
        Map<String, List<MovingPart>> movingParts = new HashMap<>();
        movingParts.put("incoming", incoming);
        movingParts.put("outgoing", outgoing);
        
        return movingParts;
    }

    /**
     * Get list of parts currently at this custom location
     * (Parts that were moved TO this location and haven't been moved FROM it)
     */
    public List<Map<String, Object>> getCurrentPartsAtLocation(Long customLocationId) {
        // Reuse the getMovingPartsForCustomLocation method to get all moving parts
        Map<String, List<MovingPart>> movingParts = getMovingPartsForCustomLocation(customLocationId);
        
        List<MovingPart> incomingParts = movingParts.get("incoming");
        List<MovingPart> outgoingParts = movingParts.get("outgoing");
        
        // Create a set of part identifiers that have left
        Set<String> partsThatLeft = outgoingParts.stream()
                .map(mp -> mp.getPartName() + "|" + (mp.getSerialNumber() != null ? mp.getSerialNumber() : ""))
                .collect(Collectors.toSet());
        
        // Filter to only parts that are still here
        List<Map<String, Object>> currentParts = incomingParts.stream()
                .filter(mp -> !partsThatLeft.contains(mp.getPartName() + "|" + (mp.getSerialNumber() != null ? mp.getSerialNumber() : "")))
                .map(mp -> {
                    Map<String, Object> partInfo = new HashMap<>();
                    partInfo.put("partName", mp.getPartName());
                    partInfo.put("serialNumber", mp.getSerialNumber());
                    partInfo.put("partNumber", mp.getPartNumber());
                    partInfo.put("quantity", mp.getQuantity());
                    partInfo.put("moveDate", mp.getMoveDate());
                    partInfo.put("movingPartId", mp.getId());
                    return partInfo;
                })
                .collect(Collectors.toList());
        
        return currentParts;
    }
}

