package ru.iis.multi.block;

/**
 * Люки многоблочных машин (GT_MetaTileEntity_Hatch_*); voltage — напряжение энергетического люка (вход) или динамо-люка
 * (выход) V[mTier], у остальных 0.
 */
public enum HatchType {
    ENERGY("hatch_energy_lv", 32),
    /** GT_Loader_MetaTileEntities:238 Hatch_Energy_MV (D82). */
    ENERGY_MV("hatch_energy_mv", 128),
    /** GT_Loader_MetaTileEntities:215–216 Hatch_Dynamo_LV/MV: выход энергии многоблочного генератора (D89). */
    DYNAMO("hatch_dynamo_lv", 32),
    DYNAMO_MV("hatch_dynamo_mv", 128),
    /** GT_Loader_MetaTileEntities:239, :217 Hatch_Energy_HV, Hatch_Dynamo_HV (D93). */
    ENERGY_HV("hatch_energy_hv", 512),
    DYNAMO_HV("hatch_dynamo_hv", 512),
    /** GT_Loader_MetaTileEntities:240, :218 Hatch_Energy_EV, Hatch_Dynamo_EV (D99). */
    ENERGY_EV("hatch_energy_ev", 2048),
    DYNAMO_EV("hatch_dynamo_ev", 2048),
    /** GT_Loader_MetaTileEntities:273, :230 Hatch_Energy_IV, Hatch_Dynamo_IV (D103). */
    ENERGY_IV("hatch_energy_iv", 8192),
    DYNAMO_IV("hatch_dynamo_iv", 8192),
    /** GT_Loader_MetaTileEntities:274, :231 Hatch_Energy_LuV, Hatch_Dynamo_LuV (D110). */
    ENERGY_LUV("hatch_energy_luv", 32768),
    DYNAMO_LUV("hatch_dynamo_luv", 32768),
    /** GT_Loader_MetaTileEntities:275, :232 Hatch_Energy_ZPM, Hatch_Dynamo_ZPM (D112). */
    ENERGY_ZPM("hatch_energy_zpm", 131072),
    DYNAMO_ZPM("hatch_dynamo_zpm", 131072),
    INPUT_BUS("hatch_input_bus_lv"),
    OUTPUT_BUS("hatch_output_bus_lv"),
    INPUT_HATCH("hatch_input_lv"),
    OUTPUT_HATCH("hatch_output_lv"),
    MAINTENANCE("hatch_maintenance"),
    /** D119: GT_MetaTileEntity_Hatch_Maintenance.AutoKit и FullAuto. */
    MAINTENANCE_SEMIAUTO("hatch_maintenance_semiauto", "MAINTENANCE", 5),
    MAINTENANCE_AUTO("hatch_maintenance_auto", "MAINTENANCE", 9),
    MUFFLER("hatch_muffler_lv"),
    /** D103: шины, люки жидкостей и выхлопные люки MV–IV (GT_Loader_MetaTileEntities:294–422): тот же вид, что у LV, но больше. */
    INPUT_BUS_MV("hatch_input_bus_mv", "INPUT_BUS", 2), INPUT_BUS_HV("hatch_input_bus_hv", "INPUT_BUS", 3), INPUT_BUS_EV("hatch_input_bus_ev", "INPUT_BUS", 4), INPUT_BUS_IV("hatch_input_bus_iv", "INPUT_BUS", 5),
    OUTPUT_BUS_MV("hatch_output_bus_mv", "OUTPUT_BUS", 2), OUTPUT_BUS_HV("hatch_output_bus_hv", "OUTPUT_BUS", 3), OUTPUT_BUS_EV("hatch_output_bus_ev", "OUTPUT_BUS", 4), OUTPUT_BUS_IV("hatch_output_bus_iv", "OUTPUT_BUS", 5),
    INPUT_HATCH_MV("hatch_input_mv", "INPUT_HATCH", 2), INPUT_HATCH_HV("hatch_input_hv", "INPUT_HATCH", 3), INPUT_HATCH_EV("hatch_input_ev", "INPUT_HATCH", 4), INPUT_HATCH_IV("hatch_input_iv", "INPUT_HATCH", 5),
    OUTPUT_HATCH_MV("hatch_output_mv", "OUTPUT_HATCH", 2), OUTPUT_HATCH_HV("hatch_output_hv", "OUTPUT_HATCH", 3), OUTPUT_HATCH_EV("hatch_output_ev", "OUTPUT_HATCH", 4), OUTPUT_HATCH_IV("hatch_output_iv", "OUTPUT_HATCH", 5),
    MUFFLER_MV("hatch_muffler_mv", "MUFFLER", 2), MUFFLER_HV("hatch_muffler_hv", "MUFFLER", 3), MUFFLER_EV("hatch_muffler_ev", "MUFFLER", 4), MUFFLER_IV("hatch_muffler_iv", "MUFFLER", 5),
    /** D110: GT_Loader_MetaTileEntities:285–413, LuV. */
    INPUT_BUS_LUV("hatch_input_bus_luv", "INPUT_BUS", 6), OUTPUT_BUS_LUV("hatch_output_bus_luv", "OUTPUT_BUS", 6), INPUT_HATCH_LUV("hatch_input_luv", "INPUT_HATCH", 6),
    OUTPUT_HATCH_LUV("hatch_output_luv", "OUTPUT_HATCH", 6), MUFFLER_LUV("hatch_muffler_luv", "MUFFLER", 6),
    /** D112: GT_Loader_MetaTileEntities:286–414, ZPM. */
    INPUT_BUS_ZPM("hatch_input_bus_zpm", "INPUT_BUS", 7), OUTPUT_BUS_ZPM("hatch_output_bus_zpm", "OUTPUT_BUS", 7), INPUT_HATCH_ZPM("hatch_input_zpm", "INPUT_HATCH", 7),
    OUTPUT_HATCH_ZPM("hatch_output_zpm", "OUTPUT_HATCH", 7), MUFFLER_ZPM("hatch_muffler_zpm", "MUFFLER", 7),
    /** GT_Loader_MetaTileEntities:402 Hatch_DataAccess_EV (D110): 4 флешки для сборочной линии. */
    DATA_ACCESS("hatch_data_access_ev");

    public final String id;
    public final long voltage;
    /** Уровень GT (LV = 1); для люков энергии — по напряжению. */
    public final int tier;
    private final String base;

    HatchType(String id) {
        this(id, 0);
    }

    HatchType(String id, long voltage) {
        this.id = id;
        this.voltage = voltage;
        this.tier = voltage <= 0 ? 1 : ru.iis.energy.VoltageTier.forVoltage(voltage).tier();
        this.base = null;
    }

    HatchType(String id, String base, int tier) {
        this.id = id;
        this.voltage = 0;
        this.tier = tier;
        this.base = base;
    }

    /** Вид люка: у шин, люков жидкостей и глушителей MV–IV — их тип LV, у остальных — сам тип. */
    public HatchType kind() {
        return base == null ? this : valueOf(base);
    }

    /** GT_MetaTileEntity_Hatch#getSlots: (уровень + 1)² — LV 4, MV 9, HV 16, EV 25, IV 36. */
    public int slots() {
        return (tier + 1) * (tier + 1);
    }

    /** GT_MetaTileEntity_Hatch_Input#countCapacity: 8000 × (уровень + 1) до HV, выше 8000 × 8 × (уровень − 3). */
    public int tank() {
        return 8000 * (tier > 3 ? (tier - 3) * 8 : tier + 1);
    }

    /** Люк с энергией (вход или выход): к нему подключаются провода. */
    public boolean energy() {
        return voltage > 0;
    }

    public boolean dynamo() {
        return this == DYNAMO || this == DYNAMO_MV || this == DYNAMO_HV || this == DYNAMO_EV || this == DYNAMO_IV || this == DYNAMO_LUV || this == DYNAMO_ZPM;
    }

    /** GT_MetaTileEntity_Hatch#getSlots: LV — 2×2. */
    public static final int BUS_SLOTS = 4;
    /** GT_MetaTileEntity_Hatch_Input#countCapacity: LV — 8000 × 2. */
    public static final int TANK = 16000;
}
