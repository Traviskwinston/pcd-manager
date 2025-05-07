package com.pcd.manager.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.pcd.manager.model.PartLineItem;
import com.pcd.manager.model.MovementEntry;

/**
 * Rma entity representing a Return Merchandise Authorization
 */
@Entity
@Table(name = "rmas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String rmaNumber;

    private String customerName;

    private String customerContact;
    private String customerEmail;
    private String customerPhone;
    
    // Company shipping information
    private String companyShipToName;
    private String companyShipToAddress;
    private String city;
    private String state;
    private String zipCode;
    private String attn;

    private String serialNumber;

    @Enumerated(EnumType.STRING)
    private RmaStatus status = RmaStatus.RMA_WRITTEN_EMAILED;

    @Enumerated(EnumType.STRING)
    private RmaPriority priority = RmaPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    private RmaReasonForRequest reasonForRequest;

    @Enumerated(EnumType.STRING)
    private DssProductLine dssProductLine;

    @Enumerated(EnumType.STRING)
    private SystemDescription systemDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;

    private String technician;

    private LocalDate receivedDate;
    
    private LocalDate writtenDate;
    private LocalDate rmaNumberProvidedDate;

    private String salesOrder;
    private String sapNotificationNumber;
    private String serviceOrder;

    @ManyToOne
    @JoinColumn(name = "tool_id")
    private Tool tool;

    @ManyToMany
    @JoinTable(
        name = "rma_parts",
        joinColumns = @JoinColumn(name = "rma_id"),
        inverseJoinColumns = @JoinColumn(name = "part_id")
    )
    private List<Part> parts = new ArrayList<>();

    private String comments;

    @OneToMany(mappedBy = "rma", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RmaDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "rma", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RmaPicture> pictures = new ArrayList<>();

    private Boolean excelFileAttached = false;
    private String laborChargeNumber;
    private Boolean shippingMemoEmailed = false;
    private String partsSourceLocation; // Took Parts (From where)

    @ManyToOne
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    // Field Technician Contact
    private String fieldTechName;
    private String fieldTechPhone;
    private String fieldTechEmail;

    // Discovery information
    private String discoveredBy;

    // Issue details
    @Column(columnDefinition = "TEXT")
    private String whyHow;
    @Column(columnDefinition = "TEXT")
    private String howContained;

    // Additional dates
    private LocalDate shippingMemoEmailedDate;
    private LocalDate partsReceivedDate;
    private LocalDate failedPartsShippedDate;
    private LocalDate installedPartsDate;
    private LocalDate failedPartsPackedDate;

    // Process impact information
    private Boolean interruptionToFlow = false;
    private Boolean interruptionToProduction = false;
    private Double downtimeHours = 0.0;
    private Boolean exposedToProcessGasOrChemicals = false;
    private Boolean purged = false;
    
    @Column(columnDefinition = "TEXT")
    private String instructionsForExposedComponent;
    
    @Column(nullable = false)
    private LocalDateTime createdDate;

    // Part line items (up to 4)
    @ElementCollection
    @CollectionTable(name = "rma_part_items", joinColumns = @JoinColumn(name = "rma_id"))
    private List<PartLineItem> partLineItems = new ArrayList<>();

    // Movement entries
    @ElementCollection
    @CollectionTable(name = "rma_movements", joinColumns = @JoinColumn(name = "rma_id"))
    private List<MovementEntry> movementEntries = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (receivedDate == null) {
            receivedDate = LocalDate.now();
        }
        if (status == null) {
            status = RmaStatus.RMA_WRITTEN_EMAILED;
        }
        if (priority == null) {
            priority = RmaPriority.MEDIUM;
        }
        if (createdDate == null) {
            createdDate = LocalDateTime.now();
        }
    }
} 