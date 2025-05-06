package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "locations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"state", "fab"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private LocationType locationType;
    
    private String address;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(nullable = true)
    private String state;

    @Column(nullable = true)
    private String fab;
    
    @Column(name = "default_location", nullable = true)
    private boolean defaultLocation = false;

    // Mapping of state names to their abbreviations
    private static final Map<String, String> STATE_ABBREVIATIONS = new HashMap<>();
    
    static {
        STATE_ABBREVIATIONS.put("Arizona", "AZ");
        STATE_ABBREVIATIONS.put("New Mexico", "NM");
        STATE_ABBREVIATIONS.put("Ireland", "IE");
        // Add more state/country abbreviations as needed
    }

    // Composite display name (e.g., "AZ F52")
    public String getDisplayName() {
        if (state == null || state.isEmpty()) {
            return "Location";
        }
        
        if (fab == null || fab.isEmpty()) {
            return state.substring(0, Math.min(state.length(), 3)).toUpperCase();
        }
        
        String stateAbbr;
        if (STATE_ABBREVIATIONS.containsKey(state)) {
            stateAbbr = STATE_ABBREVIATIONS.get(state);
        } else if (state.length() >= 2) {
            stateAbbr = state.substring(0, 2).toUpperCase();
        } else {
            stateAbbr = state.toUpperCase();
        }
        
        return stateAbbr + " F" + fab;
    }
    
    // Custom constructor without isDefault
    public Location(Long id, String state, String fab) {
        this.id = id;
        this.state = state;
        this.fab = fab;
        this.defaultLocation = false;
    }
    
    // Explicit getter and setter for defaultLocation property
    public boolean isDefaultLocation() {
        return defaultLocation;
    }
    
    public void setDefaultLocation(boolean defaultLocation) {
        this.defaultLocation = defaultLocation;
    }

    // For backward compatibility - delegate to the proper methods
    public boolean isDefault() {
        return isDefaultLocation();
    }
    
    public void setDefault(boolean defaultLocation) {
        setDefaultLocation(defaultLocation);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocationType getLocationType() {
        return locationType;
    }

    public void setLocationType(LocationType locationType) {
        this.locationType = locationType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "Location{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", displayName='" + (name + (fab != null ? " - " + fab : "")) + '\'' +
               '}';
    }
} 