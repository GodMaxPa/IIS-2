package ru.iis.cover;

/**
 * Виды покрытий GT5 (gregtech.common.covers.*). Для каждого — число режимов отвёртки и ключи сообщений
 * (Interaction_DESCRIPTION_Index_NNN из GregTech.lang.ru).
 */
public enum CoverKind {
    CONVEYOR("conveyor"),          // GT_Cover_Conveyor(400): раз в 400 тиков — до 64 предметов
    PUMP("pump"),                  // GT_Cover_Pump(32): 32 л/тик
    ARM("arm"),                    // GT_Cover_Arm(400): по слотам
    SHUTTER("shutter"),            // GT_Cover_Shutter
    CONTROLLER("controller"),      // GT_Cover_ControlsWork
    ACTIVITY_DETECTOR("activity_detector"), // GT_Cover_DoesWork
    ITEM_DETECTOR("item_detector"),         // GT_Cover_ItemMeter
    FLUID_DETECTOR("fluid_detector"),       // GT_Cover_LiquidMeter
    ENERGY_DETECTOR("energy_detector"),     // GT_Cover_EUMeter
    DRAIN("drain"),                // GT_Cover_Drain
    CRAFTING("crafting"),          // GT_Cover_Crafting
    FLUID_REGULATOR("fluid_regulator"), // GT_Cover_FluidRegulator(32): ±32 л/тик, настраивается рукой и отвёрткой (D80)
    FLUID_FILTER("fluid_filter"),
    // GT_Cover_SolarPanel (D117): только сверху, под открытым небом; ночью и в дождь — 1/8
    SOLAR("solar_panel", 1), SOLAR_8V("solar_panel_8v", 8), SOLAR_LV("solar_panel_lv", 32), SOLAR_MV("solar_panel_mv", 128), SOLAR_HV("solar_panel_hv", 512),
    SOLAR_EV("solar_panel_ev", 2048), SOLAR_IV("solar_panel_iv", 8192), SOLAR_LUV("solar_panel_luv", 32768), SOLAR_ZPM("solar_panel_zpm", 131072), SOLAR_UV("solar_panel_uv", 524288);  // GT_Cover_Fluidfilter: одна жидкость, 8 режимов вход/выход (D80)

    public final String id;
    /** Напряжение солнечной панели; 0 у прочих покрытий. */
    public final long solarVoltage;

    CoverKind(String id) {
        this(id, 0);
    }

    CoverKind(String id, long solarVoltage) {
        this.id = id;
        this.solarVoltage = solarVoltage;
    }

    /** Ключ перевода сообщения о режиме. */
    public static String msg(int index) {
        return "cover.iis.mode." + String.format("%03d", index);
    }
}
