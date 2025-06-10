package com.pcd.manager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectPriority priority = ProjectPriority.MEDIUM;

    @Column
    private LocalDate startDate;

    @Column
    private LocalDate dueDate;

    @Column
    private LocalDate completionDate;

    @Column
    private Double budget;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id")
    private Location location;

    // Many-to-many relationship with users (team members)
    @ManyToMany
    @JoinTable(
        name = "project_members",
        joinColumns = @JoinColumn(name = "project_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> members = new HashSet<>();

    // Enum for project status
    public enum ProjectStatus {
        PLANNED,
        IN_PROGRESS,
        ON_HOLD,
        COMPLETED,
        CANCELED
    }

    // Enum for project priority
    public enum ProjectPriority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }
} 