package com.pcd.manager.controller;

import com.pcd.manager.model.Location;
import com.pcd.manager.model.User;
import com.pcd.manager.service.LocationService;
import com.pcd.manager.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ControllerAdvice
public class GlobalControllerAdvice {

    private static final Logger logger = LoggerFactory.getLogger(GlobalControllerAdvice.class);
    private final LocationService locationService;
    private final UserService userService;

    @Autowired
    public GlobalControllerAdvice(LocationService locationService, UserService userService) {
        this.locationService = locationService;
        this.userService = userService;
    }

    @ModelAttribute
    public void addDefaultLocation(Model model) {
        Location defaultLocation = locationService.getDefaultLocation().orElse(null);
        model.addAttribute("defaultLocation", defaultLocation);
        
        // Add a debug attribute to help troubleshoot
        model.addAttribute("defaultLocationExists", defaultLocation != null);
        
        // Auto-set current location if user doesn't have an active site
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String username = auth.getName();
            Optional<User> userOpt = userService.getUserByUsername(username);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                
                // If user doesn't have an active site, set it to the default location
                if (user.getActiveSite() == null && defaultLocation != null) {
                    user.setActiveSite(defaultLocation);
                    userService.updateUser(user);
                    logger.info("GlobalControllerAdvice: Auto-set user {} active site to default location: {}", 
                               username, defaultLocation.getDisplayName());
                }
                
                // Set model attributes for current location
                if (user.getActiveSite() != null) {
                    model.addAttribute("currentLocation", user.getActiveSite());
                    model.addAttribute("currentLocationExists", true);
                } else {
                    model.addAttribute("currentLocationExists", false);
                }
            }
        }
    }
} 