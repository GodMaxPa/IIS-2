package ru.iis.machine;

import ru.iis.machine.logic.BoilerLogic;

/** Малые паровые котлы GT5-II. */
public enum BoilerType {
    COAL_BRONZE("boiler_coal_bronze", BoilerLogic.Kind.COAL_BRONZE, "bronzeboiler", 16000),
    COAL_STEEL("boiler_coal_steel", BoilerLogic.Kind.COAL_STEEL, "steelboiler", 32000),
    LAVA_STEEL("boiler_lava_steel", BoilerLogic.Kind.LAVA_STEEL, "steelboiler", 32000),
    SOLAR_BRONZE("boiler_solar_bronze", BoilerLogic.Kind.SOLAR_BRONZE, "solarboiler", 16000);

    public final String id;
    public final BoilerLogic.Kind kind;
    public final String gui;
    /** Ёмкость, по которой GT рисует шкалу пара в GUI (у солнечного котла шкала на 16000 при баке 32000). */
    public final int guiSteamCapacity;

    BoilerType(String id, BoilerLogic.Kind kind, String gui, int guiSteamCapacity) {
        this.id = id;
        this.kind = kind;
        this.gui = gui;
        this.guiSteamCapacity = guiSteamCapacity;
    }
}
