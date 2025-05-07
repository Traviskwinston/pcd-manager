package com.pcd.manager.model;

import jakarta.persistence.Embeddable;
import lombok.Data;
import jakarta.persistence.ManyToOne;

@Embeddable
@Data
public class MovementEntry {
    @ManyToOne
    private Tool fromTool;

    @ManyToOne
    private Tool toTool;
} 