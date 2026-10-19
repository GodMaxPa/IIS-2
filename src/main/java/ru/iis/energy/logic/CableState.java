package ru.iis.energy.logic;

/**
 * Изменяемое состояние провода: ток за текущий тик, нагрев, максимумы за последнюю секунду.
 * GT5 обновлял их каждый тик; у нас провод не тикает — состояние «догоняется» по номеру тика.
 */
public final class CableState {
    /** GT5 WireHeatingTicks=4: провод загорается, когда нагрев превысит 4×100. */
    public static final int HEATING_LIMIT = 4 * 100;

    public long transferredAmps;
    public int overheat;
    public long voltageLast20, amperageLast20;
    private long lastTick = Long.MIN_VALUE;
    private long last20Start = Long.MIN_VALUE;
    /** Провод перегрелся — его нужно сжечь. */
    public boolean burnt;

    /** Тик последнего пакета через провод. */
    public long lastTick() {
        return lastTick;
    }

    public void touch(long tick) {
        if (tick == lastTick) return;
        if (lastTick != Long.MIN_VALUE) overheat = (int) Math.max(0, overheat - Math.min(Integer.MAX_VALUE, tick - lastTick));
        lastTick = tick;
        transferredAmps = 0;
        if (tick - last20Start >= 20 || last20Start == Long.MIN_VALUE) {
            last20Start = tick;
            voltageLast20 = 0;
            amperageLast20 = 0;
        }
    }
}
