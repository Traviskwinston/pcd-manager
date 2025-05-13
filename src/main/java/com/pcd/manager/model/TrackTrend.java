package com.pcd.manager.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "track_trends")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"affectedTools", "relatedTrackTrends", "comments", "documentPaths", "picturePaths", "documentNames", "pictureNames"})
@EqualsAndHashCode(exclude = {"affectedTools", "relatedTrackTrends", "comments", "documentPaths", "picturePaths", "documentNames", "pictureNames"})
public class TrackTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToMany
    @JoinTable(
        name = "tracktrend_tools",
        joinColumns = @JoinColumn(name = "tracktrend_id"),
        inverseJoinColumns = @JoinColumn(name = "tool_id")
    )
    @JsonIgnoreProperties("currentTechnicians")
    private Set<Tool> affectedTools = new HashSet<>();
    
    @ManyToMany
    @JoinTable(
        name = "tracktrend_relations",
        joinColumns = @JoinColumn(name = "tracktrend_id"),
        inverseJoinColumns = @JoinColumn(name = "related_tracktrend_id")
    )
    @JsonIgnoreProperties({"affectedTools", "relatedTrackTrends"})
    private Set<TrackTrend> relatedTrackTrends = new HashSet<>();
    
    @OneToMany(mappedBy = "trackTrend", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("trackTrend")
    private List<TrackTrendComment> comments = new ArrayList<>();
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tracktrend_document_paths", joinColumns = @JoinColumn(name = "tracktrend_id"))
    @Column(name = "document_path")
    private Set<String> documentPaths = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tracktrend_document_names", joinColumns = @JoinColumn(name = "tracktrend_id"))
    @MapKeyColumn(name = "file_path")
    @Column(name = "file_name")
    private Map<String, String> documentNames = new HashMap<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tracktrend_picture_paths", joinColumns = @JoinColumn(name = "tracktrend_id"))
    @Column(name = "picture_path")
    private Set<String> picturePaths = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tracktrend_picture_names", joinColumns = @JoinColumn(name = "tracktrend_id"))
    @MapKeyColumn(name = "file_path")
    @Column(name = "file_name")
    private Map<String, String> pictureNames = new HashMap<>();

    // Helper method to safely access comments
    public List<TrackTrendComment> getComments() {
        if (this.comments == null) {
            this.comments = new ArrayList<>();
        }
        return this.comments;
    }
} 