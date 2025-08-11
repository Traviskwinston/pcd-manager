package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tool_checklist_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolChecklistTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_type", unique = true, nullable = false)
    private String toolType; // Enum name from Tool.ToolType

    @Lob
    @Column(name = "items_json", nullable = false)
    private String itemsJson; // JSON array of { key, label }

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}


