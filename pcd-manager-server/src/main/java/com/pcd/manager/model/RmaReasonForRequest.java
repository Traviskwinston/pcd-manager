package com.pcd.manager.model;

/**
 * Represents the reason for an RMA request
 */
public enum RmaReasonForRequest {
    LABOR_ONLY("Labor Only"),
    NON_WARRANTY_FAR_NO_REPLACEMENT("Non-Warranty FAR - No Replacement"),
    RETURN_FOR_CREDIT("Return for Credit"),
    RETURN_FOR_REFURBISHMENT("Return for Refurbishment"),
    RETURNED_GOODS_AUTHORIZATION("Returned Goods Authorization"),
    TRACK_AND_TREND_NO_REPLACEMENT("Track and Trend - No Replacement"),
    WARRANTY_REPLACEMENT("Warranty Replacement"),
    WARRANTY_REPLACEMENT_AND_FAR("Warranty Replacement and FAR");

    private final String displayName;

    RmaReasonForRequest(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
} 