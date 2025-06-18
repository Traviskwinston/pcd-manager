package com.pcd.manager.controller;

import com.pcd.manager.model.User;
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
        // Cache the default location - this is now cached at service level
        Location defaultLocation = locationService.getDefaultLocation().orElse(null);
        model.addAttribute("defaultLocation", defaultLocation);
        model.addAttribute("defaultLocationExists", defaultLocation != null);
        
        // Only process user location logic for authenticated users
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String username = auth.getName();
            Optional<User> userOpt = userService.getUserByUsername(username);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                
                // Only auto-set active site if user doesn't have one AND we have a default location
                // This prevents unnecessary database writes on every request
                if (user.getActiveSite() == null && defaultLocation != null) {
                    try {
                        user.setActiveSite(defaultLocation);
                        userService.updateUser(user);
                        logger.debug("GlobalControllerAdvice: Auto-set user {} active site to default location: {}", 
                                   username, defaultLocation.getDisplayName());
                    } catch (Exception e) {
                        logger.warn("Failed to auto-set active site for user {}: {}", username, e.getMessage());
                    }
                }
                
                // Set model attributes for current location
                Location currentLocation = user.getActiveSite();
                model.addAttribute("currentLocation", currentLocation);
                model.addAttribute("currentLocationExists", currentLocation != null);
            } else {
                // User not found - set safe defaults
                model.addAttribute("currentLocationExists", false);
            }
        } else {
            // Not authenticated - set safe defaults
            model.addAttribute("currentLocationExists", false);
        }
    }
} 