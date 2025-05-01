package com.pcd.manager.controller;

import com.pcd.manager.model.Location;
import com.pcd.manager.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@CrossOrigin(origins = "*")
public class LocationApiController {

    private final LocationService locationService;

    @Autowired
    public LocationApiController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public ResponseEntity<List<Location>> getAllLocations() {
        return ResponseEntity.ok(locationService.getAllLocations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Location> getLocationById(@PathVariable Long id) {
        return locationService.getLocationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/default")
    public ResponseEntity<Location> getDefaultLocation() {
        return locationService.getDefaultLocation()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<Location> setDefaultLocation(@PathVariable Long id) {
        try {
            locationService.setDefaultLocation(id);
            return locationService.getLocationById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Location> createLocation(@RequestBody Location location) {
        location.setId(null); // Ensure a new entity is created
        Location savedLocation = locationService.saveLocation(location);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedLocation);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Location> updateLocation(@PathVariable Long id, @RequestBody Location location) {
        return locationService.getLocationById(id)
                .map(existingLocation -> {
                    location.setId(id);
                    Location updatedLocation = locationService.saveLocation(location);
                    return ResponseEntity.ok(updatedLocation);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLocation(@PathVariable Long id) {
        return locationService.getLocationById(id)
                .map(location -> {
                    locationService.deleteLocation(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
} 