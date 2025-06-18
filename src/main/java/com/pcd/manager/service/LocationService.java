package com.pcd.manager.service;

import com.pcd.manager.model.Location;
import com.pcd.manager.repository.LocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
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

    @Cacheable(value = "locations-list", key = "'all-locations'")
    public List<Location> getAllLocations() {
        logger.debug("Fetching all locations (cacheable)");
        return locationRepository.findAll();
    }

    public Optional<Location> getLocationById(Long id) {
        return locationRepository.findById(id);
    }

    @Cacheable(value = "default-location", key = "'default'")
    public Optional<Location> getDefaultLocation() {
        logger.debug("Fetching default location from database (cacheable)");
        
        // Use the more efficient single query method
        Optional<Location> location = locationRepository.findByDefaultLocationIsTrue();
        
        if (location.isPresent()) {
            logger.debug("Found default location: {} (displayName: {})", 
                        location.get().getId(), 
                        location.get().getDisplayName());
        } else {
            logger.warn("No default location found");
        }
        
        return location;
    }

    @CacheEvict(value = {"locations-list", "dropdown-data", "default-location"}, allEntries = true)
    public Location saveLocation(Location location) {
        if (location.isDefaultLocation()) {
            logger.debug("Clearing previous default locations");
            locationRepository.clearDefaultLocations();
        }
        Location saved = locationRepository.save(location);
        logger.debug("Saved location: {} (default: {}) and evicted caches", saved.getId(), saved.isDefaultLocation());
        return saved;
    }

    @CacheEvict(value = {"locations-list", "dropdown-data", "default-location"}, allEntries = true)
    public void deleteLocation(Long id) {
        logger.debug("Deleting location {} and evicting caches", id);
        locationRepository.deleteById(id);
    }

    @CacheEvict(value = {"locations-list", "dropdown-data", "default-location"}, allEntries = true)
    public void setDefaultLocation(Long id) {
        logger.debug("Setting location {} as default and evicting caches", id);
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