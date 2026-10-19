package ru.iis.energy.logic;

/**
 * Одноблочный генератор GT5-II на жидком топливе (GT_MetaTileEntity_BasicGenerator), без Minecraft.
 *
 * Правила оригинала:
 *  - раз в 10 тиков сжигается столько топлива, сколько влезает в запас: операций = min(жидкость / расход, свободно / ценность);
 *  - ценность топлива за операцию = энергия топлива × КПД / 100 × расход (у паровой турбины — 3 EU за 6+уровень литров);
 *  - запас = V × 40 + минимальный запас; выход — 1 ампер V через лицевую сторону;
 *  - генератор «активен», когда запас ≥ V + минимальный запас.
 */
public final class GeneratorLogic {
    public final long voltage;
    public final long minimumStored;

    public GeneratorLogic(long voltage, long minimumStored) {
        this.voltage = voltage;
        this.minimumStored = minimumStored;
    }

    public long capacity() {
        return voltage * 40 + minimumStored;
    }

    /**
     * Сжечь топливо. Возвращает израсходованные литры.
     * @param fluidAmount литров в баке
     * @param fuelValuePerOperation EU за одну операцию
     * @param consumedPerOperation литров за операцию
     */
    public long burn(EnergyStorage energy, long fluidAmount, long fuelValuePerOperation, long consumedPerOperation) {
        if (fuelValuePerOperation <= 0 || consumedPerOperation <= 0 || fluidAmount <= consumedPerOperation) return 0;
        long ops = Math.min(fluidAmount / consumedPerOperation, (capacity() - energy.stored) / fuelValuePerOperation);
        if (ops <= 0) return 0;
        energy.stored += ops * fuelValuePerOperation;
        return ops * consumedPerOperation;
    }

    public boolean active(EnergyStorage energy) {
        return energy.stored >= voltage + minimumStored;
    }

    /** Сколько ампер можно выпустить сейчас с учётом цены выпуска (EnergyNet#emissionCost). */
    public long usableAmperes(EnergyStorage energy, long maxAmperesOut) {
        return Math.max(0, Math.min(maxAmperesOut, (energy.stored - minimumStored) / EnergyNet.emissionCost(voltage)));
    }

    /** Паровая турбина GT5: 3 EU за (6 + уровень) литров пара; LV — 7 л → 3 EU (КПД 86%). */
    public static long steamTurbineLitersPerOperation(int tier) {
        return 6 + tier;
    }

    public static final long STEAM_TURBINE_EU_PER_OPERATION = 3;

    /** Дизель и газовая турбина GT5 (ИИС MachineStats.cfg): КПД 100 − 5 × уровень %. */
    public static int combustionEfficiency(int tier) {
        return 100 - 5 * tier;
    }
}
