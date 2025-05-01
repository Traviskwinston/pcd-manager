package com.pcd.manager.model;

public enum PartCategory {
    ELECTRONIC("Electronic"),
    MECHANICAL("Mechanical"),
    HYDRAULIC("Hydraulic"),
    PNEUMATIC("Pneumatic"),
    ELECTRICAL("Electrical"),
    OTHER("Other");

    private final String displayName;

    PartCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
} 