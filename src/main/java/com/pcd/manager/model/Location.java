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
    
    private String address;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(nullable = true)
    private String state;

    @Column(nullable = true)
    private String fab;
    
    @Column(name = "display_name", nullable = true)
    private String displayName;
    
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
        // Return stored displayName if available
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        
        // Fall back to computed display name for backward compatibility
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

    @Override
    public String toString() {
        return "Location{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", displayName='" + (name + (fab != null ? " - " + fab : "")) + '\'' +
               '}';
    }
} 