package ru.iis.energy;

/**
 * Запас EU машины GT5 и приём энергии из сети (BaseMetaTileEntity#injectEnergyUnits). Без зависимостей от Minecraft.
 *
 * Правила оригинала:
 *  - пакет выше входного напряжения машины — взрыв (здесь: {@link InjectResult#OVERVOLTAGE}, взрывает вызывающий);
 *  - за тик принимается не больше maxAmperesIn ампер;
 *  - полная машина энергию не принимает, но последний пакет может «переполнить» запас (как в GT5);
 *  - выдача (для генераторов и буферов) — {@link #drain}.
 */
public final class EuStorage {
    public long stored;
    public long capacity;
    /** Входное напряжение (0 — машина энергию не принимает). */
    public long inputVoltage;
    /** Приём ампер за тик (машины GT5 считают его от текущего рецепта). */
    public long maxAmperesIn = 1;
    private long acceptedAmperes;
    private long tickId = Long.MIN_VALUE;

    public EuStorage(long capacity, long inputVoltage) {
        this.capacity = capacity;
        this.inputVoltage = inputVoltage;
    }

    public enum Kind { OK, OVERVOLTAGE }

    public record InjectResult(Kind kind, long amperes) {
        public static final InjectResult NONE = new InjectResult(Kind.OK, 0);
        public static final InjectResult OVERVOLTAGE = new InjectResult(Kind.OVERVOLTAGE, 0);
    }

    /** Новый тик: сбросить счётчик принятых ампер. */
    public void beginTick(long tick) {
        if (tick != tickId) {
            tickId = tick;
            acceptedAmperes = 0;
        }
    }

    public InjectResult inject(long tick, long voltage, long amperage) {
        beginTick(tick);
        if (inputVoltage <= 0 || amperage <= 0 || voltage <= 0 || stored >= capacity || maxAmperesIn <= acceptedAmperes) {
            return InjectResult.NONE;
        }
        if (voltage > inputVoltage) return InjectResult.OVERVOLTAGE;
        long amps = Math.min(amperage, Math.min(maxAmperesIn - acceptedAmperes, 1 + (capacity - stored) / voltage));
        stored += voltage * amps;
        acceptedAmperes += amps;
        return new InjectResult(Kind.OK, amps);
    }

    /** Списать EU. false — если не хватает (ничего не списано). */
    public boolean drain(long eu) {
        if (stored - eu < 0) return false;
        stored -= eu;
        return true;
    }

    public long acceptedThisTick() {
        return acceptedAmperes;
    }

    /** Сила взрыва GT5 (MetaTileEntity#doExplosion) для энергии данного напряжения. */
    public static float explosionStrength(long voltage) {
        long[] v = {8, 32, 128, 512, 2048};
        if (voltage < v[0]) return 1f;
        if (voltage < v[1]) return 2f;
        if (voltage < v[2]) return 3f;
        if (voltage < v[3]) return 4f;
        if (voltage < v[4]) return 5f;
        if (voltage < v[4] * 2) return 6f;
        if (voltage < 8192) return 7f;
        if (voltage < 32768) return 8f;
        if (voltage < 131072) return 9f;
        return 10f;
    }

    /** Списание при излучении: GT5 берёт V + 2^уровень EU за ампер (LV: 34 за пакет 32). */
    public static long emissionCost(long voltage) {
        return Math.max(voltage, voltage + (1L << VoltageTier.forVoltage(voltage).tier()));
    }

    /** Урон от удара током голого провода (GT_Utility#applyElectricityDamage): уровень × амперы × 4. */
    public static float shockDamage(long voltage, long amperage) {
        if (voltage <= 0 || amperage <= 0) return 0;
        return VoltageTier.forVoltage(voltage).tier() * amperage * 4f;
    }
}
