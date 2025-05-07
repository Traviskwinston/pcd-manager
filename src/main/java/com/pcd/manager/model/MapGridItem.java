package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents an item placed on the facility map grid
 */
@Entity
@Data
@Table(name = "map_grid_items")
public class MapGridItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType type;

    @Column(nullable = false)
    private Integer x;

    @Column(nullable = false)
    private Integer y;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    // For drawing items
    private String text;
    private String color;
    private Boolean isSolid;

    // For tool items
    @ManyToOne
    @JoinColumn(name = "tool_id")
    private Tool tool;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }

    public enum ItemType {
        TOOL,
        DRAWING
    }
} 