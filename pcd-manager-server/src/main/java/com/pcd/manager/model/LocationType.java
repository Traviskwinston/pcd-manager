package com.pcd.manager.model;

public enum LocationType {
    STORAGE("Storage"),
    WORKSTATION("Workstation"),
    OFFICE("Office"),
    FIELD("Field"),
    OTHER("Other");

    private final String displayName;

    LocationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
} 