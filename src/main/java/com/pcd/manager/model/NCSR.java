package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "ncsrs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NCSR {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tool_id")
    private Tool tool;

    // Status enum: OPEN or CLOSED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NcsrStatus status = NcsrStatus.OPEN;

    // Installed checkbox (synced with status)
    @Column(nullable = false)
    private Boolean installed = false;

    // Install date (separate from other dates, can be null even if installed)
    @Column(name = "install_date")
    private LocalDate installDate;

    // Versum/EMD Quote
    @Column(name = "versum_emd_quote", length = 500)
    private String versumEmdQuote;

    // Customer Location (separate from Tool location)
    @Column(name = "customer_location", length = 500)
    private String customerLocation;

    // Customer PO#
    @Column(name = "customer_po", length = 255)
    private String customerPo;

    // Customer PO Received Date
    @Column(name = "customer_po_received_date")
    private LocalDate customerPoReceivedDate;

    // Supplier
    @Column(length = 500)
    private String supplier;

    // Supplier PO# or Production Order
    @Column(name = "supplier_po_or_production_order", length = 500)
    private String supplierPoOrProductionOrder;

    // Finish Date
    @Column(name = "finish_date")
    private LocalDate finishDate;

    // MM# (Model Number)
    @Column(name = "mm_number", length = 255)
    private String mmNumber;

    // Equipment # (used for matching with Tool serial numbers)
    @Column(name = "equipment_number", length = 255)
    private String equipmentNumber;

    // Serial #
    @Column(name = "serial_number", length = 255)
    private String serialNumber;

    // Description
    @Column(length = 1000)
    private String description;

    // Tool ID# (separate identifier, not the actual Tool entity ID)
    @Column(name = "tool_id_number", length = 255)
    private String toolIdNumber;

    // Component
    @Column(length = 500)
    private String component;

    // Discrepant Part Mfg
    @Column(name = "discrepant_part_mfg", length = 500)
    private String discrepantPartMfg;

    // Discrepant Part Number
    @Column(name = "discrepant_part_number", length = 255)
    private String discrepantPartNumber;

    // Part Location/I.D.
    @Column(name = "part_location_id", length = 500)
    private String partLocationId;

    // Part Quantity
    @Column(name = "part_quantity")
    private Integer partQuantity;

    // Est Ship Date
    @Column(name = "est_ship_date")
    private LocalDate estShipDate;

    // ECR#
    @Column(name = "ecr_number", length = 255)
    private String ecrNumber;

    // Contract Manufacturer
    @Column(name = "contract_manufacturer", length = 500)
    private String contractManufacturer;

    // Tracking # from Supplier to FSE
    @Column(name = "tracking_number_supplier_to_fse", length = 500)
    private String trackingNumberSupplierToFse;

    // Notification to Robin (Where shipped)
    @Column(name = "notification_to_robin", length = 1000)
    private String notificationToRobin;

    // Work Instruction Required?
    @Column(name = "work_instruction_required")
    private Boolean workInstructionRequired = false;

    // Work Instruction Identifier
    @Column(name = "work_instruction_identifier", length = 500)
    private String workInstructionIdentifier;

    // FSE Field Service Completion Date
    @Column(name = "fse_field_service_completion_date")
    private LocalDate fseFieldServiceCompletionDate;

    // Tool Owner
    @Column(name = "tool_owner", length = 500)
    private String toolOwner;

    // Comments
    @Column(columnDefinition = "TEXT")
    private String comments;

    public enum NcsrStatus {
        OPEN("Open"),
        CLOSED("Closed");

        private final String displayName;

        NcsrStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
} 