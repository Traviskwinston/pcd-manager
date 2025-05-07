package com.pcd.manager.model;

/**
 * Represents the System Description for RMA requests
 */
public enum SystemDescription {
    BLEND_MODULE("Blend Module"),
    CHEMBLEND("ChemBlend"),
    DISTRIBUTION_MODULE("Distribution Module"),
    FEED_MODULE("Feed Module"),
    SCADA("SCADA"),
    OTHER("Other (Enter Description in Failure Mode)");

    private final String displayName;

    SystemDescription(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
} 