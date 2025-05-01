package com.pcd.manager.model;

public enum RmaPriority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    URGENT("Urgent");
    
    private final String displayName;
    
    RmaPriority(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
} 