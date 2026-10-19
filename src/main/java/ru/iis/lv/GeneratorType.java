package ru.iis.lv;

/**
 * Генераторы LV (GT_MetaTileEntity_BasicGenerator и наследники): бак и КПД.
 * Паровая турбина: 3 EU за 7 л пара (mEfficiency = 6 + уровень). Дизель и газовая турбина: 1 л топлива =
 * энергия топлива × КПД (100 − 5 × уровень = 95 %), бак 16000 л (ИИС MachineStats.cfg).
 */
public enum GeneratorType {
    STEAM_TURBINE("steam_turbine_lv", 24000, "steam"),
    DIESEL("diesel_generator_lv", 16000, "diesel"),
    GAS_TURBINE("gas_turbine_lv", 16000, "gas");

    public final String id;
    public final int tank;
    /** Раздел в iis/fuels.json (для паровой турбины не используется). */
    public final String fuelKind;

    GeneratorType(String id, int tank, String fuelKind) {
        this.id = id;
        this.tank = tank;
        this.fuelKind = fuelKind;
    }
}
