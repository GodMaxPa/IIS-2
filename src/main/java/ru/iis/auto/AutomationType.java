package ru.iis.auto;

/**
 * Автоматика GT5-II (D108, GT_MetaTileEntity_Buffer и наследники): слоты для хранения, слоты-образцы и экран GT.
 * gui — имя файла textures/gui/auto/.
 */
public enum AutomationType {
    CHEST_BUFFER("chest_buffer", 27, 0, 3, "chest_buffer"),
    SUPER_BUFFER("super_buffer", 256, 0, 3, "super_buffer"),
    FILTER("item_filter", 9, 9, 5, "item_filter"),
    TYPE_FILTER("type_filter", 9, 0, 5, "type_filter"),
    REGULATOR("regulator", 9, 9, 1, "regulator"),
    ITEM_DISTRIBUTOR("item_distributor", 27, 0, 3, "item_distributor");

    public final String id, gui;
    /** Слоты хранения, слоты-образцы (идут следом), кнопки в ряду y = 63. */
    public final int storage, ghosts, buttons;

    AutomationType(String id, int storage, int ghosts, int buttons, String gui) {
        this.id = id;
        this.storage = storage;
        this.ghosts = ghosts;
        this.buttons = buttons;
        this.gui = gui;
    }

    public int slots() {
        return storage + ghosts;
    }
}
