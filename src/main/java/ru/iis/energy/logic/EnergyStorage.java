package ru.iis.energy.logic;

/**
 * Запас EU блока GT (BaseMetaTileEntity: mStoredEnergy + injectEnergyUnits/decreaseStoredEU), без Minecraft.
 *
 * Правила оригинала (GT5-II BaseMetaTileEntity#injectEnergyUnits):
 *  - пакет напряжением выше входного уровня — взрыв, энергия не принимается;
 *  - за тик принимается не больше maxAmperesIn ампер;
 *  - запас может переполниться на один пакет («1 + свободное место / напряжение»), как в GT5.
 */
public final class EnergyStorage {
    public long stored;
    public long capacity;
    /** Наибольшее допустимое входное напряжение (0 — не принимает). */
    public long inputVoltage;
    /** Сколько ампер принимается за тик. */
    public long maxAmperesIn = 1;
    private long acceptedThisTick;
    private long lastTick = Long.MIN_VALUE;

    public EnergyStorage(long capacity, long inputVoltage) {
        this.capacity = capacity;
        this.inputVoltage = inputVoltage;
    }

    /** Результат подачи пакета: принято ампер или взрыв от перенапряжения. */
    public record Inject(long amperes, boolean overvoltage) {
        public static final Inject NONE = new Inject(0, false);
    }

    public Inject inject(long tick, long voltage, long amperes) {
        if (tick != lastTick) {
            lastTick = tick;
            acceptedThisTick = 0;
        }
        if (inputVoltage <= 0 || amperes <= 0 || voltage <= 0 || stored >= capacity || maxAmperesIn <= acceptedThisTick) return Inject.NONE;
        if (voltage > inputVoltage) return new Inject(0, true);
        long a = Math.min(amperes, Math.min(maxAmperesIn - acceptedThisTick, 1 + (capacity - stored) / voltage));
        stored += voltage * a;
        acceptedThisTick += a;
        return new Inject(a, false);
    }

    /** Забрать энергию; false — если не хватает (тогда ничего не забирается). */
    public boolean drain(long eu) {
        if (stored - eu < 0) return false;
        stored -= eu;
        return true;
    }

    public void add(long eu) {
        stored = Math.min(capacity, stored + eu);
    }
}
