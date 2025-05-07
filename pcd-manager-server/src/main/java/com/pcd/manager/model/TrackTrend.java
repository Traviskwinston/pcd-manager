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

@Entity
@Table(name = "track_trends")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "affectedTools")
@EqualsAndHashCode(exclude = "affectedTools")
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
} 