package com.pcd.manager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "parts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Part name is required")
    private String name;

    @NotBlank(message = "Part number is required")
    @Column(unique = true)
    private String partNumber;
    
    private String description;
     
    
    private String manufacturer;
    
    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;
    
    @NotNull
    @Min(0)
    private Integer quantity = 0;
    
    @Enumerated(EnumType.STRING)
    private PartCategory category;
    
    @Min(0)
    private Integer minimumQuantity = 0;
    
    private Double unitCost;
    
    private String supplier;
    
    private LocalDate lastOrderDate;
    
    private Boolean replacementRequired = false;
    
    @Column(length = 1000)
    private String notes;
    
    public enum PartCategory {
        ELECTRICAL, MECHANICAL, HYDRAULIC, PNEUMATIC, STRUCTURAL, FASTENER, CONSUMABLE, OTHER
    }
} 