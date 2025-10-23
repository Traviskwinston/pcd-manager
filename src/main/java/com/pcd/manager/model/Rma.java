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
import com.pcd.manager.model.LaborEntry;
import java.math.BigDecimal;

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

    @Column(name = "reference_number")
    private String referenceNumber; // Can be either RMA number or SAP notification number

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
    private String notes;

    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;

    private String technician;

    private LocalDate receivedDate;
    
    private LocalDate writtenDate;
    private LocalDate rmaNumberProvidedDate;

    private String salesOrder;
    private String serviceOrder;
    private String returnMaterialsTo;

    @ManyToOne
    @JoinColumn(name = "tool_id")
    private Tool tool;

    /**
     * New: Multiple affected tools per RMA. Backfilled from legacy tool_id.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rma_tools",
        joinColumns = @JoinColumn(name = "rma_id"),
        inverseJoinColumns = @JoinColumn(name = "tool_id")
    )
    private Set<Tool> affectedTools = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "rma_parts",
        joinColumns = @JoinColumn(name = "rma_id"),
        inverseJoinColumns = @JoinColumn(name = "part_id")
    )
    private List<Part> parts = new ArrayList<>();

    @OneToMany(mappedBy = "rma", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RmaComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "rma", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RmaDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "rma", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
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
    @Column(columnDefinition = "TEXT")
    private String problemDiscoverer; // Who discovered the problem
    
    private LocalDate problemDiscoveryDate; // When was it discovered

    // Issue details
    @Column(columnDefinition = "TEXT")
    private String whatHappened; // What happened
    
    @Column(columnDefinition = "TEXT")
    private String whyAndHowItHappened; // Why and How did it happen
    
    @Column(columnDefinition = "TEXT")
    private String howContained; // How was it Contained
    
    @Column(columnDefinition = "TEXT")
    private String whoContained; // Who contained it

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
    private Boolean startupSo3Complete = false;
    private Boolean failedOnInstall = false;
    private Boolean purgedAndDoubleBaggedGoodsEnclosed = false;
    
    @Column(columnDefinition = "TEXT")
    private String instructionsForExposedComponent;
    
    @Column(nullable = false)
    private LocalDateTime createdDate;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Part line items (up to 4)
    @ElementCollection
    @CollectionTable(name = "rma_part_items", joinColumns = @JoinColumn(name = "rma_id"))
    private List<PartLineItem> partLineItems = new ArrayList<>();

    // Movement entries
    @ElementCollection
    @CollectionTable(name = "rma_movements", joinColumns = @JoinColumn(name = "rma_id"))
    private List<MovementEntry> movementEntries = new ArrayList<>();

    // Labor entries
    @ElementCollection
    @CollectionTable(name = "rma_labor_entries", joinColumns = @JoinColumn(name = "rma_id"))
    private List<LaborEntry> laborEntries = new ArrayList<>();

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
        LocalDateTime now = LocalDateTime.now();
        if (createdDate == null) {
            createdDate = now;
        }
        updatedAt = now;
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate total labor cost from all labor entries
     * @return total labor cost
     */
    public BigDecimal getTotalLaborCost() {
        if (laborEntries == null || laborEntries.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return laborEntries.stream()
                .map(LaborEntry::getExtendedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Ensure collections are never null
    public List<RmaDocument> getDocuments() {
        if (documents == null) {
            documents = new ArrayList<>();
        }
        return documents;
    }

    public List<RmaPicture> getPictures() {
        if (pictures == null) {
            pictures = new ArrayList<>();
        }
        return pictures;
    }
    
    // Convenience methods for backward compatibility and semantic clarity
    
    /**
     * Gets the reference number (can be RMA number or SAP notification number)
     * @return the reference number
     */
    public String getRmaNumber() {
        return referenceNumber;
    }
    
    /**
     * Sets the reference number (can be RMA number or SAP notification number)
     * @param rmaNumber the reference number to set
     */
    public void setRmaNumber(String rmaNumber) {
        this.referenceNumber = rmaNumber;
    }
    
    /**
     * Gets the reference number (can be RMA number or SAP notification number)
     * @return the reference number
     */
    public String getSapNotificationNumber() {
        return referenceNumber;
    }
    
    /**
     * Sets the reference number (can be RMA number or SAP notification number)
     * @param sapNotificationNumber the reference number to set
     */
    public void setSapNotificationNumber(String sapNotificationNumber) {
        this.referenceNumber = sapNotificationNumber;
    }
    
    /**
     * Gets the reference number directly
     * @return the reference number
     */
    public String getReferenceNumber() {
        return referenceNumber;
    }
    
    /**
     * Sets the reference number directly
     * @param referenceNumber the reference number to set
     */
    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }
} 