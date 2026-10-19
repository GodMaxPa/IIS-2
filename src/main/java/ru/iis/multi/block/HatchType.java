package ru.iis.multi.block;

/** Люки многоблочных машин LV (GT_MetaTileEntity_Hatch_*). */
public enum HatchType {
    ENERGY("hatch_energy_lv"),
    INPUT_BUS("hatch_input_bus_lv"),
    OUTPUT_BUS("hatch_output_bus_lv"),
    INPUT_HATCH("hatch_input_lv"),
    OUTPUT_HATCH("hatch_output_lv"),
    MAINTENANCE("hatch_maintenance"),
    MUFFLER("hatch_muffler_lv");

    public final String id;

    HatchType(String id) {
        this.id = id;
    }

    /** GT_MetaTileEntity_Hatch#getSlots: LV — 2×2. */
    public static final int BUS_SLOTS = 4;
    /** GT_MetaTileEntity_Hatch_Input#countCapacity: LV — 8000 × 2. */
    public static final int TANK = 16000;
}
