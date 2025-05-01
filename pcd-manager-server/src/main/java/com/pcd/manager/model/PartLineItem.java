package com.pcd.manager.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Min;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartLineItem {

    private String partName;
    private String partNumber;
    private String productDescription;

    @Min(0)
    private Integer quantity = 1; // Default quantity to 1 for new lines
    
    private Boolean replacementRequired = false; // Default to false for new lines

} 