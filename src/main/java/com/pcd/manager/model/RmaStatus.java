package com.pcd.manager.model;

public enum RmaStatus {
    RMA_WRITTEN_EMAILED("RMA Written - Emailed"),
    NUMBER_PROVIDED("RMA Number Provided"),
    MEMO_EMAILED("Shipping Memo Emailed"),
    RECEIVED_PARTS("Received Parts"),
    WAITING_CUSTOMER("Waiting on Customer"),
    WAITING_ENGINEERING("Waiting on Engineering"),
    WAITING_FSE("Waiting on FSE"),
    MISSING_LABOR_HOURS("Missing Labor Hours"),
    COMPLETED("Completed");
    
    private final String displayName;
    
    RmaStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
} 