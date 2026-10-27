package ru.iis.lv;

import ru.iis.energy.VoltageTier;

/**
 * Генераторы на жидком топливе (GT_MetaTileEntity_BasicGenerator и наследники): бак, уровень и раздел топлива.
 * Паровая турбина: 3 EU за (6 + уровень) л пара, бак 24000 × уровень. Дизель и газовая турбина: 1 л топлива =
 * энергия топлива × КПД (100 − 5 × уровень %), бак 16000 л (ИИС MachineStats.cfg). HV — D94, MV — D95.
 */
public enum GeneratorType {
    STEAM_TURBINE("steam_turbine_lv", 24000, "steam", VoltageTier.LV),
    DIESEL("diesel_generator_lv", 16000, "diesel", VoltageTier.LV),
    GAS_TURBINE("gas_turbine_lv", 16000, "gas", VoltageTier.LV),
    // GT_Loader_MetaTileEntities:1164, 1174, 1182 (mTier 2, D95)
    STEAM_TURBINE_MV("steam_turbine_mv", 24000 * 2, "steam", VoltageTier.MV),
    DIESEL_MV("diesel_generator_mv", 16000, "diesel", VoltageTier.MV),
    GAS_TURBINE_MV("gas_turbine_mv", 16000, "gas", VoltageTier.MV),
    // GT_Loader_MetaTileEntities:1165, 1175, 1183 (mTier 3)
    STEAM_TURBINE_HV("steam_turbine_hv", 24000 * 3, "steam", VoltageTier.HV),
    DIESEL_HV("diesel_generator_hv", 16000, "diesel", VoltageTier.HV),
    GAS_TURBINE_HV("gas_turbine_hv", 16000, "gas", VoltageTier.HV);

    public final String id;
    public final int tank;
    /** Раздел в iis/fuels.json; "steam" — паровая турбина (топливо — пар). */
    public final String fuelKind;
    public final VoltageTier tier;

    GeneratorType(String id, int tank, String fuelKind, VoltageTier tier) {
        this.id = id;
        this.tank = tank;
        this.fuelKind = fuelKind;
        this.tier = tier;
    }

    public boolean steam() {
        return "steam".equals(fuelKind);
    }

    public long voltage() {
        return tier.voltage();
    }

    /** GT getPollution: BASE_POLLUTION × 2^(уровень − 1) (дизель 2, газовая турбина 1); паровая турбина не загрязняет. */
    public int pollution() {
        int base = switch (fuelKind) {
            case "diesel" -> ru.iis.pollution.PollutionLogic.DIESEL_BASE;
            case "gas" -> ru.iis.pollution.PollutionLogic.GAS_TURBINE_BASE;
            default -> 0;
        };
        return base << (tier.tier() - 1);
    }
}
