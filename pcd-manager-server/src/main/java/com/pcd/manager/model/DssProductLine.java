package com.pcd.manager.model;

/**
 * Represents the DSS Product Line for RMA requests
 */
public enum DssProductLine {
    CARRIER_CLEANER("Carrier Cleaner"),
    CHEMGUARD("ChemGuard® Chemical Delivery Systems"),
    CHEMKEEPER("ChemKeeper Delivery Systems"),
    FLOWMASTER("FlowMaster®"),
    FTC_SYSTEMS("FTC (Flow and Temp Control) Systems"),
    GASGUARD("GASGUARD® Gas Delivery Systems"),
    GASKEEPER("Gaskeeper Gas Delivery Systems"),
    GASSTAR("GasSTAR"),
    VMHYT_TOOL_GAS_JUNGLES("VMHYT Tool Gas Jungles"),
    PARTS_CLEAN("Parts Clean"),
    QMAC("QMAC Analytical Systems"),
    SCADA_SYSTEMS("SCADA Systems: MMMS, GCS, CMS, GMS"),
    OTHER_ABQ("Other (ABQ)"),
    OTHER_VULTEE("Other (Vultee)"),
    OTHER_VMHYT("Other (VMHYT)");

    private final String displayName;

    DssProductLine(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
} 