package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import jakarta.persistence.FetchType;

@Entity
@Table(name = "passdowns")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"user", "tools", "assignedTechs", "picturePaths", "pictureNames", "documentPaths", "documentNames"})
@EqualsAndHashCode(exclude = {"user", "tools", "assignedTechs", "picturePaths", "pictureNames", "documentPaths", "documentNames"})
public class Passdown {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "VARCHAR(10000)", nullable = true)
    private String comment;

    @Column(nullable = true)
    private LocalDate date = LocalDate.now();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    // User who created the passdown
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    // Many-to-Many: A passdown can have multiple tools
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "passdown_tools",
        joinColumns = @JoinColumn(name = "passdown_id"),
        inverseJoinColumns = @JoinColumn(name = "tool_id")
    )
    private Set<Tool> tools = new HashSet<>();
    
    // Many-to-Many: A passdown can be assigned to multiple technicians
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "passdown_techs",
        joinColumns = @JoinColumn(name = "passdown_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> assignedTechs = new HashSet<>();

    // Pictures with upload tracking
    @OneToMany(mappedBy = "passdown", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PassdownPicture> pictures = new ArrayList<>();

    // Legacy string-based picture tracking - deprecated but kept for migration compatibility
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "passdown_pictures_legacy", joinColumns = @JoinColumn(name = "passdown_id"))
    @Column(name = "picture_path")
    private Set<String> picturePaths = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "passdown_picture_names_legacy", joinColumns = @JoinColumn(name = "passdown_id"))
    @MapKeyColumn(name = "picture_path")
    @Column(name = "original_filename")
    private Map<String, String> pictureNames = new HashMap<>();

    // Store document paths
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "passdown_documents", joinColumns = @JoinColumn(name = "passdown_id"))
    @Column(name = "document_path")
    private Set<String> documentPaths = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "passdown_document_names", joinColumns = @JoinColumn(name = "passdown_id"))
    @MapKeyColumn(name = "document_path")
    @Column(name = "original_filename")
    private Map<String, String> documentNames = new HashMap<>();

    @PrePersist
    public void prePersist() {
        if (createdDate == null) {
            createdDate = LocalDateTime.now();
        }
        if (date == null) {
            date = LocalDate.now();
        }
    }
} 