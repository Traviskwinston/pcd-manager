package com.pcd.manager.controller;

import com.pcd.manager.model.Location;
import com.pcd.manager.repository.LocationRepository;
import com.pcd.manager.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
@RequestMapping("/locations")
public class LocationController {

    private final LocationRepository locationRepository;
    private final LocationService locationService;

    @Autowired
    public LocationController(LocationRepository locationRepository, LocationService locationService) {
        this.locationRepository = locationRepository;
        this.locationService = locationService;
    }
    
    /**
     * Adds the default location to all model attributes
     * This will be available in all controller methods
     */
    @ModelAttribute
    public void addDefaultLocation(Model model) {
        Optional<Location> defaultLocation = locationService.getDefaultLocation();
        defaultLocation.ifPresent(location -> model.addAttribute("defaultLocation", location));
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

    @PostMapping("/{id}/delete")
    public String deleteLocation(@PathVariable Long id) {
        locationRepository.deleteById(id);
        return "redirect:/locations";
    }
} 