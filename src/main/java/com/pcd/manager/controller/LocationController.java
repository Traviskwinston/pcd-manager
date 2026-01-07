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
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/locations")
public class LocationController {

    private final LocationRepository locationRepository;
    private final LocationService locationService;
    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(LocationController.class);

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
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String username = auth.getName();
            Optional<User> userOpt = userService.getUserByUsername(username);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                
                // If user doesn't have an active site, set it to the default location
                if (user.getActiveSite() == null && defaultLocation.isPresent()) {
                    user.setActiveSite(defaultLocation.get());
                    userService.updateUser(user);
                    logger.info("Auto-set user {} active site to default location: {}", 
                               username, defaultLocation.get().getDisplayName());
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
    public String saveLocation(@Valid @ModelAttribute("location") Location location, BindingResult result, 
                              Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "locations/form";
        }
        
        // Validate state and fab are not empty
        if (location.getState() == null || location.getState().trim().isEmpty()) {
            result.rejectValue("state", "error.state", "State is required");
            return "locations/form";
        }
        
        if (location.getFab() == null || location.getFab().trim().isEmpty()) {
            result.rejectValue("fab", "error.fab", "Fab is required");
            return "locations/form";
        }
        
        // Check for duplicate state/fab combination (case-insensitive)
        // Only if this is a new location or if state/fab changed
        Optional<Location> existingLocation = locationRepository.findByStateAndFabIgnoreCase(
            location.getState().trim(), 
            location.getFab().trim()
        );
        
        if (existingLocation.isPresent()) {
            // If editing an existing location, allow if it's the same location
            if (location.getId() == null || !existingLocation.get().getId().equals(location.getId())) {
                result.rejectValue("fab", "error.duplicate", 
                    "A location with State '" + location.getState() + "' and Fab '" + location.getFab() + "' already exists.");
                logger.warn("Attempted to create duplicate location: state={}, fab={}", 
                           location.getState(), location.getFab());
                return "locations/form";
            }
        }
        
        try {
            // If this location is set as default, clear any other default locations
            if (location.isDefault()) {
                locationRepository.clearDefaultLocations();
            }
            
            // Trim state and fab before saving
            location.setState(location.getState().trim());
            location.setFab(location.getFab().trim());
            if (location.getDisplayName() != null) {
                location.setDisplayName(location.getDisplayName().trim());
            }
            
            Location saved = locationRepository.save(location);
            logger.info("Saved location: id={}, state={}, fab={}, displayName={}", 
                       saved.getId(), saved.getState(), saved.getFab(), saved.getDisplayName());
            
            redirectAttributes.addFlashAttribute("message", 
                "Location '" + saved.getDisplayName() + "' saved successfully.");
            return "redirect:/locations";
            
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            logger.error("Database constraint violation saving location: state={}, fab={}, error={}", 
                        location.getState(), location.getFab(), e.getMessage());
            result.rejectValue("fab", "error.duplicate", 
                "A location with State '" + location.getState() + "' and Fab '" + location.getFab() + "' already exists.");
            return "locations/form";
        } catch (Exception e) {
            logger.error("Error saving location: {}", e.getMessage(), e);
            result.reject("error.save", "An error occurred while saving the location: " + e.getMessage());
            return "locations/form";
        }
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

    @PostMapping("/{id}/update")
    @ResponseBody
    public ResponseEntity<?> updateLocation(@PathVariable Long id, @RequestBody Map<String, String> updates) {
        try {
            Optional<Location> locationOpt = locationRepository.findById(id);
            if (!locationOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            Location location = locationOpt.get();
            
            // Update fields if provided
            if (updates.containsKey("state")) {
                String state = updates.get("state").trim();
                if (state.isEmpty()) {
                    return ResponseEntity.badRequest().body("State cannot be empty");
                }
                location.setState(state);
            }
            
            if (updates.containsKey("fab")) {
                String fab = updates.get("fab").trim();
                if (fab.isEmpty()) {
                    return ResponseEntity.badRequest().body("Fab cannot be empty");
                }
                location.setFab(fab);
            }
            
            if (updates.containsKey("displayName")) {
                String displayName = updates.get("displayName").trim();
                if (displayName.isEmpty()) {
                    return ResponseEntity.badRequest().body("Display Name cannot be empty");
                }
                location.setDisplayName(displayName);
            }
            
            // Save the updated location
            Location savedLocation = locationRepository.save(location);
            
            // Return the updated location data
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedLocation.getId());
            response.put("state", savedLocation.getState());
            response.put("fab", savedLocation.getFab());
            response.put("displayName", savedLocation.getDisplayName());
            response.put("message", "Location updated successfully");
            
            logger.info("Updated location {}: state={}, fab={}, displayName={}", 
                       id, savedLocation.getState(), savedLocation.getFab(), savedLocation.getDisplayName());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating location {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Error updating location: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteLocation(@PathVariable Long id) {
        locationRepository.deleteById(id);
        return "redirect:/locations";
    }
} 