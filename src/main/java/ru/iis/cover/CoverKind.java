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
    CRAFTING("crafting");          // GT_Cover_Crafting

    public final String id;

    CoverKind(String id) {
        this.id = id;
    }

    /** Ключ перевода сообщения о режиме. */
    public static String msg(int index) {
        return "cover.iis.mode." + String.format("%03d", index);
    }
}
