package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tool_type_field_definitions", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"tool_type", "field_key"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolTypeFieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_type", nullable = false)
    private String toolType;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "field_label", nullable = false)
    private String fieldLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    private FieldType fieldType;

    @Column(name = "dropdown_options_json", columnDefinition = "TEXT")
    @JsonIgnore
    private String dropdownOptionsJson;

    @Column(name = "is_required")
    private Boolean isRequired = false;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum FieldType {
        TEXT, NUMBER, DROPDOWN, DATE, BOOLEAN
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse dropdown options from JSON string
     * Exposed as JSON property for API responses
     */
    @JsonGetter("dropdownOptions")
    public List<String> getDropdownOptions() {
        if (dropdownOptionsJson == null || dropdownOptionsJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(dropdownOptionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Set dropdown options as JSON string
     */
    public void setDropdownOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            this.dropdownOptionsJson = null;
        } else {
            try {
                this.dropdownOptionsJson = objectMapper.writeValueAsString(options);
            } catch (Exception e) {
                this.dropdownOptionsJson = null;
            }
        }
    }
}

