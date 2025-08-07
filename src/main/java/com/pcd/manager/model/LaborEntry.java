package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Labor entry for RMA labor tracking
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LaborEntry {

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "technician")
    private String technician;

    @Column(name = "hours", precision = 8, scale = 2)
    private BigDecimal hours = BigDecimal.ZERO;

    @Column(name = "labor_date")
    private LocalDate laborDate;

    @Column(name = "price_per_hour", precision = 8, scale = 2)
    private BigDecimal pricePerHour = BigDecimal.ZERO;

    /**
     * Calculate extended cost (hours * price per hour)
     * @return extended cost
     */
    public BigDecimal getExtendedCost() {
        if (hours == null || pricePerHour == null) {
            return BigDecimal.ZERO;
        }
        return hours.multiply(pricePerHour);
    }
}