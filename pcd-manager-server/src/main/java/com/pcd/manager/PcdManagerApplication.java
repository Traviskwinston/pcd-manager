package com.pcd.manager;

import com.pcd.manager.model.Location;
import com.pcd.manager.repository.LocationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class PcdManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PcdManagerApplication.class, args);
    }

    @Bean
    @Transactional
    public CommandLineRunner initializeData(LocationRepository locationRepository) {
        return args -> {
            // Define all the fab locations we want to ensure exist
            Map<String, List<String>> allFabs = new HashMap<>();
            allFabs.put("Arizona", Arrays.asList("12", "32", "42", "52"));
            allFabs.put("Ireland", Arrays.asList("24"));
            allFabs.put("New Mexico", Arrays.asList("11", "11x"));
            
            // Get existing locations
            List<Location> existingLocations = locationRepository.findAll();
            
            // Check which fabs already exist
            Map<String, List<String>> existingFabs = existingLocations.stream()
                .collect(Collectors.groupingBy(
                    Location::getState,
                    Collectors.mapping(Location::getFab, Collectors.toList())
                ));
            
            boolean addedNewLocations = false;
            
            // Add any missing fabs
            for (Map.Entry<String, List<String>> entry : allFabs.entrySet()) {
                String state = entry.getKey();
                List<String> fabs = entry.getValue();
                
                List<String> existingStateFabs = existingFabs.getOrDefault(state, List.of());
                
                for (String fab : fabs) {
                    if (!existingStateFabs.contains(fab)) {
                        // This fab doesn't exist yet, add it
                        Location newLocation = new Location(null, state, fab);
                        locationRepository.save(newLocation);
                        addedNewLocations = true;
                        System.out.println("Added missing location: " + state + " F" + fab);
                    }
                }
            }
            
            if (addedNewLocations) {
                System.out.println("Missing fab locations have been added to the database.");
            }
            
            // If no default location is set, set AZ Fab 52 as default
            Long defaultId = locationRepository.findDefaultLocationId();
            if (defaultId == null) {
                locationRepository.findAll().stream()
                    .filter(loc -> "Arizona".equals(loc.getState()) && "52".equals(loc.getFab()))
                    .findFirst()
                    .ifPresent(location -> {
                        location.setDefaultLocation(true);
                        locationRepository.save(location);
                        System.out.println("Set AZ F52 as the default location.");
                    });
            }
        };
    }
} 