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
    
    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolStatus status = ToolStatus.NOT_STARTED;
    
    @Column
    private LocalDate setDate;
    
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
    @ElementCollection
    @CollectionTable(name = "tool_documents", joinColumns = @JoinColumn(name = "tool_id"))
    @Column(name = "document_path")
    private Set<String> documentPaths = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "tool_document_names", joinColumns = @JoinColumn(name = "tool_id"))
    @MapKeyColumn(name = "document_path")
    @Column(name = "original_filename")
    private Map<String, String> documentNames = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "tool_pictures", joinColumns = @JoinColumn(name = "tool_id"))
    @Column(name = "picture_path")
    private Set<String> picturePaths = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "tool_picture_names", joinColumns = @JoinColumn(name = "tool_id"))
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
    @ElementCollection
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

    @OneToMany(mappedBy = "toTool", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<MovingPart> partsMovedTo = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_tool_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Tool activeTool;

    public enum ToolType {
        CHEMBLEND, SLURRY
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