package com.pcd.manager.service;

import com.pcd.manager.model.Location;
import com.pcd.manager.repository.LocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

@Service
public class LocationService {
    private static final Logger logger = LoggerFactory.getLogger(LocationService.class);

    private final LocationRepository locationRepository;

    @Autowired
    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public List<Location> getAllLocations() {
        return locationRepository.findAll();
    }

    public Optional<Location> getLocationById(Long id) {
        return locationRepository.findById(id);
    }

    public Optional<Location> getDefaultLocation() {
        Long defaultId = locationRepository.findDefaultLocationId();
        logger.debug("Default location ID from repository: {}", defaultId);
        
        if (defaultId == null) {
            logger.warn("No default location ID found");
            return Optional.empty();
        }
        
        Optional<Location> location = locationRepository.findById(defaultId);
        if (location.isPresent()) {
            logger.debug("Found default location: {} (displayName: {})", 
                        location.get().getId(), 
                        location.get().getDisplayName());
        } else {
            logger.warn("Default location with ID {} not found", defaultId);
        }
        
        return location;
    }

    public Location saveLocation(Location location) {
        if (location.isDefaultLocation()) {
            logger.debug("Clearing previous default locations");
            locationRepository.clearDefaultLocations();
        }
        Location saved = locationRepository.save(location);
        logger.debug("Saved location: {} (default: {})", saved.getId(), saved.isDefaultLocation());
        return saved;
    }

    public void deleteLocation(Long id) {
        locationRepository.deleteById(id);
    }

    public void setDefaultLocation(Long id) {
        logger.debug("Setting location {} as default", id);
        locationRepository.clearDefaultLocations();
        locationRepository.findById(id).ifPresent(location -> {
            location.setDefaultLocation(true);
            locationRepository.save(location);
            logger.debug("Location {} set as default", id);
        });
    }

    /**
     * Initializes the default location if none exists.
     * Creates and sets "AZ Fab 52" as the default location if it doesn't exist yet.
     */
    @PostConstruct
    @Transactional
    public void initializeDefaultLocation() {
        logger.info("Initializing default location");
        Long defaultId = locationRepository.findDefaultLocationId();
        logger.debug("Current default location ID: {}", defaultId);
        
        if (defaultId == null) {
            logger.info("No default location found, looking for Arizona Fab 52");
            // Check if we already have a location for Arizona Fab 52
            List<Location> locations = getAllLocations();
            logger.debug("Found {} total locations", locations.size());
            
            Optional<Location> azFab52 = locations.stream()
                .filter(loc -> "Arizona".equals(loc.getState()) && "52".equals(loc.getFab()))
                .findFirst();
            
            if (azFab52.isPresent()) {
                // Set existing location as default
                logger.info("Found existing Arizona Fab 52 location (ID: {}), setting as default", azFab52.get().getId());
                setDefaultLocation(azFab52.get().getId());
            } else {
                // Create and set Arizona Fab 52 as default
                logger.info("Creating new Arizona Fab 52 location and setting as default");
                Location defaultLocation = new Location();
                defaultLocation.setState("Arizona");
                defaultLocation.setFab("52");
                defaultLocation.setDefaultLocation(true);
                Location saved = locationRepository.save(defaultLocation);
                logger.info("Created default location with ID: {}", saved.getId());
            }
        } else {
            logger.info("Default location already exists with ID: {}", defaultId);
        }
    }
} 