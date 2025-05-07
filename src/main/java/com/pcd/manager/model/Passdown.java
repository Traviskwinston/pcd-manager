package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import jakarta.persistence.FetchType;

@Entity
@Table(name = "passdowns")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"user", "tool", "picturePaths", "pictureNames", "documentPaths", "documentNames"})
@EqualsAndHashCode(exclude = {"user", "tool", "picturePaths", "pictureNames", "documentPaths", "documentNames"})
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_id")
    private Tool tool;

    // Store multiple picture paths and original names
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "passdown_pictures", joinColumns = @JoinColumn(name = "passdown_id"))
    @Column(name = "picture_path")
    private Set<String> picturePaths = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "passdown_picture_names", joinColumns = @JoinColumn(name = "passdown_id"))
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