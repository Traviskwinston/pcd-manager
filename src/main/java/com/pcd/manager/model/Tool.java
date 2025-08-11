package com.pcd.manager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "tools")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({
    "currentTechnicians",
    "partMovements",
    "partsMovedFrom",
    "partsMovedTo",
    "documentPaths",
    "documentNames",
    "documentTags",
    "picturePaths",
    "pictureNames"
})
public class Tool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Primary tool name is required")
    @Column(nullable = false)
    private String name;

    @Column
    private String secondaryName;
    
    @Enumerated(EnumType.STRING)
    private ToolType toolType;
    
    @Column
    private String serialNumber1;
    
    @Column
    private String serialNumber2;
    
    @Column
    private String model1;
    
    @Column
    private String model2;

    @Column
    private String chemicalGasService;

    // GasGuard-specific metadata (optional for other types)
    @Column(name = "system_name")
    private String systemName;

    @Column(name = "equipment_location")
    private String equipmentLocation;

    @Column(name = "config_number")
    private String configNumber;

    // Stored as an integer percentage (e.g., 100)
    @Column(name = "equipment_set")
    private Integer equipmentSet;

    // Snapshot of checklist labels when tool becomes active (at least one item checked)
    @Column(name = "checklist_labels_json")
    private String checklistLabelsJson;
    
    // Location as simple string field (converted from foreign key for performance)
    @Column(name = "location_name", nullable = false)
    private String locationName;
    
    // Legacy location relationship - kept for potential rollback
    // @ManyToOne
    // @JoinColumn(name = "location_id")
    // private Location location;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolStatus status = ToolStatus.NOT_STARTED;
    
    @Column
    private LocalDate setDate;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "upload_date")
    private LocalDateTime uploadDate;
    
    @Column(length = 1000)
    private String notes;
    
    // Dates for tool checklist (replacing boolean flags with completion dates)
    @Column
    private LocalDate commissionDate;

    @Column
    private LocalDate preSl1Date;

    @Column
    private LocalDate sl1Date;

    @Column
    private LocalDate sl2Date;

    @Column
    private LocalDate electricalOperationPreSl1Date;

    @Column
    private LocalDate hazardousEnergyChecklistDate;

    @Column
    private LocalDate mechanicalPreSl1Date;

    @Column
    private LocalDate mechanicalPostSl1Date;

    @Column
    private LocalDate specificInputFunctionalityDate;

    @Column
    private LocalDate modesOfOperationDate;

    @Column
    private LocalDate specificSoosDate;

    @Column
    private LocalDate fieldServiceReportDate;

    @Column
    private LocalDate certificateOfApprovalDate;

    @Column
    private LocalDate turnedOverToCustomerDate;
    
    @Column
    private LocalDate startUpSl03Date;

    // Document and picture paths
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_documents", joinColumns = @JoinColumn(name = "tool_id"))
    @Column(name = "document_path")
    private Set<String> documentPaths = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_document_names", joinColumns = @JoinColumn(name = "tool_id"))
    @MapKeyColumn(name = "document_path")
    @Column(name = "original_filename")
    private Map<String, String> documentNames = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_document_tags", joinColumns = @JoinColumn(name = "tool_id"))
    @MapKeyColumn(name = "document_path")
    @Column(name = "document_tag")
    private Map<String, String> documentTags = new HashMap<>();

    // Pictures with upload tracking
    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ToolPicture> pictures = new ArrayList<>();

    // Legacy string-based picture tracking - deprecated but kept for migration compatibility
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_pictures_legacy", joinColumns = @JoinColumn(name = "tool_id"))
    @Column(name = "picture_path")
    private Set<String> picturePaths = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_picture_names_legacy", joinColumns = @JoinColumn(name = "tool_id"))
    @MapKeyColumn(name = "picture_path")
    @Column(name = "original_filename")
    private Map<String, String> pictureNames = new HashMap<>();

    // Current technicians assigned to the tool
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "tool_technicians",
        joinColumns = @JoinColumn(name = "tool_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<User> currentTechnicians = new HashSet<>();

    // Tags for the tool
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_tags", joinColumns = @JoinColumn(name = "tool_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    // Parts history (parts taken out or put in)
    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<PartMovement> partMovements = new ArrayList<>();

    // Moving parts relationships
    @OneToMany(mappedBy = "fromTool", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<MovingPart> partsMovedFrom = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_tool_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Tool activeTool;

    /**
     * Track and Trend entries this tool is part of
     */
    @ManyToMany(mappedBy = "affectedTools", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Set<TrackTrend> trackTrends = new HashSet<>();

    /**
     * Comments associated with this tool
     */
    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private List<ToolComment> comments = new ArrayList<>();

    public enum ToolType {
        CHEMBLEND("ChemBlend"),
        SLURRY("Slurry"),
        AMATGASGUARD("GasGuard");
        
        private final String displayName;
        
        ToolType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum ToolStatus {
        NOT_STARTED, IN_PROGRESS, COMPLETED
    }
    
    // Helper methods to check if a checklist item is completed
    public boolean isCommissionCompleted() {
        return commissionDate != null;
    }
    
    public boolean isPreSl1Completed() {
        return preSl1Date != null;
    }
    
    public boolean isSl1Completed() {
        return sl1Date != null;
    }
    
    public boolean isSl2Completed() {
        return sl2Date != null;
    }
    
    public boolean isElectricalOperationPreSl1Completed() {
        return electricalOperationPreSl1Date != null;
    }
    
    public boolean isHazardousEnergyChecklistCompleted() {
        return hazardousEnergyChecklistDate != null;
    }
    
    public boolean isMechanicalPreSl1Completed() {
        return mechanicalPreSl1Date != null;
    }
    
    public boolean isMechanicalPostSl1Completed() {
        return mechanicalPostSl1Date != null;
    }
    
    public boolean isSpecificInputFunctionalityTested() {
        return specificInputFunctionalityDate != null;
    }
    
    public boolean isModesOfOperationTested() {
        return modesOfOperationDate != null;
    }
    
    public boolean isSpecificSoosTestsed() {
        return specificSoosDate != null;
    }
    
    public boolean isFieldServiceReportUploaded() {
        return fieldServiceReportDate != null;
    }
    
    public boolean isCertificateOfApprovalUploaded() {
        return certificateOfApprovalDate != null;
    }
    
    public boolean isTurnedOverToCustomer() {
        return turnedOverToCustomerDate != null;
    }
    
    public boolean isStartUpSl03Completed() {
        return startUpSl03Date != null;
    }
    
    /**
     * Calculate the dynamic status based on checklist completion
     */
    public ToolStatus getCalculatedStatus() {
        int completedItems = 0;
        
        // Count all checklist items that are completed
        if (isCommissionCompleted()) completedItems++;
        if (isPreSl1Completed()) completedItems++;
        if (isSl1Completed()) completedItems++;
        if (isMechanicalPreSl1Completed()) completedItems++;
        if (isMechanicalPostSl1Completed()) completedItems++;
        if (isSpecificInputFunctionalityTested()) completedItems++;
        if (isModesOfOperationTested()) completedItems++;
        if (isSpecificSoosTestsed()) completedItems++;
        if (isFieldServiceReportUploaded()) completedItems++;
        if (isCertificateOfApprovalUploaded()) completedItems++;
        if (isTurnedOverToCustomer()) completedItems++;
        if (isStartUpSl03Completed()) completedItems++;
        
        // Total checklist items = 12
        if (completedItems == 0) {
            return ToolStatus.NOT_STARTED;
        } else if (completedItems == 12) {
            return ToolStatus.COMPLETED;
        } else {
            return ToolStatus.IN_PROGRESS;
        }
    }
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Tool{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", secondaryName='" + secondaryName + '\'' +
               ", toolType=" + toolType +
               ", serialNumber1='" + serialNumber1 + '\'' +
               ", serialNumber2='" + serialNumber2 + '\'' +
               ", status=" + status +
               '}';
    }
} 