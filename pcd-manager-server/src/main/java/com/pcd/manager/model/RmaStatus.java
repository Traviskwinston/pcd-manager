package com.pcd.manager.model;

public enum RmaStatus {
    RMA_WRITTEN_EMAILED("RMA Written, Emailed to support"),
    NUMBER_PROVIDED("RMA Number Provided"),
    MEMO_EMAILED("Shipping Memo Emailed"),
    RECEIVED_PARTS("Received Parts"),
    WAITING_CUSTOMER("Waiting on Customer"),
    WAITING_FSE("Waiting on FSE"),
    COMPLETED("Completed");
    
    private final String displayName;
    
    RmaStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
} 