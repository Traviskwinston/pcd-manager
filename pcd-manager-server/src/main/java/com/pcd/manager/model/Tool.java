package com.pcd.manager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    
    // Checkboxes for tool checklist (stored as boolean flags)
    @Column
    private boolean preSl1Completed;

    @Column
    private boolean sl1Completed;

    @Column
    private boolean sl2Completed;

    @Column
    private boolean electricalOperationPreSl1Completed;

    @Column
    private boolean hazardousEnergyChecklistCompleted;

    @Column
    private boolean mechanicalPreSl1Completed;

    @Column
    private boolean mechanicalPostSl1Completed;

    @Column
    private boolean specificInputFunctionalityTested;

    @Column
    private boolean modesOfOperationTested;

    @Column
    private boolean specificSoosTestsed;

    @Column
    private boolean fieldServiceReportUploaded;

    @Column
    private boolean certificateOfApprovalUploaded;

    @Column
    private boolean turnedOverToCustomer;

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
    @ManyToMany
    @JoinTable(
        name = "tool_technicians",
        joinColumns = @JoinColumn(name = "tool_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> currentTechnicians = new HashSet<>();

    // Tags for the tool
    @ElementCollection
    @CollectionTable(name = "tool_tags", joinColumns = @JoinColumn(name = "tool_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    // Parts history (parts taken out or put in)
    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PartMovement> partMovements = new ArrayList<>();

    public enum ToolType {
        CHEMBLEND, SLURRY
    }
    
    public enum ToolStatus {
        NOT_STARTED, IN_PROGRESS, COMPLETED
    }
} 