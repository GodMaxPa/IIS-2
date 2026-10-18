package ru.iis.machine;

import ru.iis.energy.VoltageTier;

/**
 * Разгон (overclock) одноблочной машины GT5.
 * Перенесено из GT_MetaTileEntity_BasicMachine#calculateOverclockedNess (GT5-II).
 *
 * Правила оригинала:
 *  - рецепт до 16 EU/t: каждый уровень машины выше LV даёт x4 к потреблению и /2 к времени;
 *  - рецепт выше 16 EU/t: умножаем потребление на 4 и делим время на 2,
 *    пока потребление не превышает напряжение предыдущего уровня * ампераж
 *    (после последнего умножения потребление укладывается в уровень машины).
 *
 * Отличие от оригинала (см. docs/PORTING_NOTES.md): время не может стать меньше 1 тика.
 * В GT5 целочисленное деление могло дать 0.
 */
public final class Overclock {
    private Overclock() {}

    public record Result(long euPerTick, int durationTicks) {}

    /**
     * @param recipeEuPerTick потребление по рецепту, EU/t (> 0)
     * @param recipeDuration  длительность по рецепту, тики (> 0)
     * @param machineTier     уровень машины (LV и выше)
     * @param amperage        ампераж машины (обычно 1)
     */
    public static Result basicMachine(long recipeEuPerTick, int recipeDuration, VoltageTier machineTier, int amperage) {
        if (recipeEuPerTick <= 0) throw new IllegalArgumentException("EU/t must be positive");
        if (recipeDuration <= 0) throw new IllegalArgumentException("Duration must be positive");
        if (amperage <= 0) throw new IllegalArgumentException("Amperage must be positive");
        int tier = machineTier.tier();
        if (tier < VoltageTier.LV.tier()) throw new IllegalArgumentException("Basic machines start at LV");

        long eut;
        long duration;
        if (recipeEuPerTick <= 16) {
            long factor = 1L << (tier - 1);
            eut = recipeEuPerTick * factor * factor;
            duration = recipeDuration / factor;
        } else {
            eut = recipeEuPerTick;
            duration = recipeDuration;
            long limit = VoltageTier.byTier(tier - 1).voltage() * amperage;
            while (eut <= limit) {
                eut *= 4;
                duration /= 2;
            }
        }
        return new Result(eut, (int) Math.max(1, duration));
    }
}
