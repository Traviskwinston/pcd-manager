package com.pcd.manager.controller;

import com.pcd.manager.model.Location;
import com.pcd.manager.service.LocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

@ControllerAdvice
public class BaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseController.class);
    
    private final LocationService locationService;
    
    @Autowired
    public BaseController(LocationService locationService) {
        this.locationService = locationService;
    }
    
    @ModelAttribute("defaultLocation")
    public Location getDefaultLocation() {
        try {
            Optional<Location> defaultLocation = locationService.getDefaultLocation();
            return defaultLocation.orElse(null);
        } catch (Exception e) {
            logger.error("Error fetching default location in global ModelAttribute: {}", e.getMessage());
            return null;
        }
    }
} 