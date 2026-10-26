package ru.iis.multi.block;

/** Люки многоблочных машин (GT_MetaTileEntity_Hatch_*); voltage — вход энергетического люка V[mTier], у остальных 0. */
public enum HatchType {
    ENERGY("hatch_energy_lv", 32),
    /** GT_Loader_MetaTileEntities:238 Hatch_Energy_MV (D82). */
    ENERGY_MV("hatch_energy_mv", 128),
    INPUT_BUS("hatch_input_bus_lv"),
    OUTPUT_BUS("hatch_output_bus_lv"),
    INPUT_HATCH("hatch_input_lv"),
    OUTPUT_HATCH("hatch_output_lv"),
    MAINTENANCE("hatch_maintenance"),
    MUFFLER("hatch_muffler_lv");

    public final String id;
    public final long voltage;

    HatchType(String id) {
        this(id, 0);
    }

    HatchType(String id, long voltage) {
        this.id = id;
        this.voltage = voltage;
    }

    public boolean energy() {
        return voltage > 0;
    }

    /** GT_MetaTileEntity_Hatch#getSlots: LV — 2×2. */
    public static final int BUS_SLOTS = 4;
    /** GT_MetaTileEntity_Hatch_Input#countCapacity: LV — 8000 × 2. */
    public static final int TANK = 16000;
}
