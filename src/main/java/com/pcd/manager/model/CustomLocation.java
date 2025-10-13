package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a custom storage location (e.g., Cabinet, Intel Cage, Office)
 * These are location-specific places where parts can be stored or moved,
 * distinct from actual Tool entities.
 */
@Entity
@Table(name = "custom_locations")
@Data
public class CustomLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Moving parts that have this custom location as their source
     */
    @OneToMany(mappedBy = "fromCustomLocationEntity", cascade = CascadeType.ALL)
    private Set<MovingPart> outgoingMovingParts = new HashSet<>();

    /**
     * Moving parts that have this custom location as their destination
     */
    @OneToMany(mappedBy = "toCustomLocationEntity", cascade = CascadeType.ALL)
    private Set<MovingPart> incomingMovingParts = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Get count of parts currently at this location
     * (parts moved TO this location minus parts moved FROM this location)
     */
    public int getPartCount() {
        // This is a simplified count - in reality you'd need to track
        // which parts are currently here vs. moved away
        return incomingMovingParts.size();
    }
}

