package com.pcd.manager.controller;

import com.pcd.manager.model.Location;
import com.pcd.manager.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final LocationService locationService;

    @Autowired
    public GlobalControllerAdvice(LocationService locationService) {
        this.locationService = locationService;
    }

    @ModelAttribute
    public void addDefaultLocation(Model model) {
        Location defaultLocation = locationService.getDefaultLocation().orElse(null);
        model.addAttribute("defaultLocation", defaultLocation);
        
        // Add a debug attribute to help troubleshoot
        model.addAttribute("defaultLocationExists", defaultLocation != null);
    }
} 