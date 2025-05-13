package com.pcd.manager.controller;

import com.pcd.manager.model.Location;
import com.pcd.manager.model.User;
import com.pcd.manager.repository.LocationRepository;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.service.LocationService;
import com.pcd.manager.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/locations")
public class LocationController {

    private final LocationRepository locationRepository;
    private final LocationService locationService;
    private final UserService userService;

    @Autowired
    public LocationController(LocationRepository locationRepository, LocationService locationService, UserService userService) {
        this.locationRepository = locationRepository;
        this.locationService = locationService;
        this.userService = userService;
    }
    
    /**
     * Adds the default location to all model attributes
     * This will be available in all controller methods
     */
    @ModelAttribute
    public void addDefaultLocation(Model model) {
        Optional<Location> defaultLocation = locationService.getDefaultLocation();
        defaultLocation.ifPresent(location -> model.addAttribute("defaultLocation", location));
        
        // Get the current user's active location if set
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            userService.getUserByUsername(username).ifPresent(user -> {
                if (user.getActiveSite() != null) {
                    model.addAttribute("currentLocation", user.getActiveSite());
                    model.addAttribute("currentLocationExists", true);
                } else {
                    model.addAttribute("currentLocationExists", false);
                }
            });
        }
    }

    @GetMapping
    public String listLocations(Model model) {
        model.addAttribute("locations", locationRepository.findAll());
        return "locations/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("location", new Location());
        return "locations/form";
    }

    @GetMapping("/{id}")
    public String showLocation(@PathVariable Long id, Model model) {
        locationRepository.findById(id).ifPresent(location -> model.addAttribute("location", location));
        return "locations/view";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        locationRepository.findById(id).ifPresent(location -> model.addAttribute("location", location));
        return "locations/form";
    }

    @PostMapping("/save")
    public String saveLocation(@Valid @ModelAttribute("location") Location location, BindingResult result) {
        if (result.hasErrors()) {
            return "locations/form";
        }
        
        // If this location is set as default, clear any other default locations
        if (location.isDefault()) {
            locationRepository.clearDefaultLocations();
        }
        
        locationRepository.save(location);
        return "redirect:/locations";
    }

    @PostMapping("/{id}/default")
    public String setDefaultLocation(@PathVariable Long id) {
        locationRepository.clearDefaultLocations();
        Location location = locationRepository.findById(id).orElseThrow();
        location.setDefault(true);
        locationRepository.save(location);
        return "redirect:/locations";
    }
    
    @PostMapping("/switch/{id}")
    public String switchToLocation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        // Get the current authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            Optional<User> userOpt = userService.getUserByUsername(username);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                
                // Get the location
                Optional<Location> locationOpt = locationRepository.findById(id);
                if (locationOpt.isPresent()) {
                    // Set the user's active site to this location
                    user.setActiveSite(locationOpt.get());
                    userService.updateUser(user);
                    
                    redirectAttributes.addFlashAttribute("message", 
                        "Switched to location: " + locationOpt.get().getDisplayName());
                } else {
                    redirectAttributes.addFlashAttribute("error", "Location not found");
                }
            } else {
                redirectAttributes.addFlashAttribute("error", "User not found");
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "You must be logged in to switch locations");
        }
        
        return "redirect:/locations";
    }

    @PostMapping("/{id}/delete")
    public String deleteLocation(@PathVariable Long id) {
        locationRepository.deleteById(id);
        return "redirect:/locations";
    }
} 