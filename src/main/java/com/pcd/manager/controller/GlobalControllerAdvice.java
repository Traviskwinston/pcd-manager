package com.pcd.manager.controller;

import com.pcd.manager.model.User;
import com.pcd.manager.model.Location;
import com.pcd.manager.service.UserService;
import com.pcd.manager.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.util.Optional;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final UserService userService;
    private final LocationService locationService;

    @Autowired
    public GlobalControllerAdvice(UserService userService, LocationService locationService) {
        this.userService = userService;
        this.locationService = locationService;
    }

    @ModelAttribute("currentUserGlobally")
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            // Use existing method, ensure it fetches activeSite if lazy
            return userService.getUserByUsername(authentication.getName()).orElse(null);
        }
        return null;
    }

    @ModelAttribute("currentLocationDisplay")
    public String getCurrentLocationDisplay(@ModelAttribute("currentUserGlobally") User currentUser) {
        if (currentUser != null && currentUser.getActiveSite() != null) {
            return currentUser.getActiveSite().getDisplayName();
        } else {
            Optional<Location> defaultLocationOpt = locationService.getDefaultLocation();
            if (defaultLocationOpt.isPresent()) {
                return "Default: " + defaultLocationOpt.get().getDisplayName();
            }
        }
        return "No Location Set";
    }

    // It might be useful to also expose the active Location object itself
    @ModelAttribute("activeLocationGlobally")
    public Location getActiveLocation(@ModelAttribute("currentUserGlobally") User currentUser) {
        if (currentUser != null && currentUser.getActiveSite() != null) {
            return currentUser.getActiveSite();
        } else {
            return locationService.getDefaultLocation().orElse(null);
        }
    }
} 