package ru.iis.multi.logic;

/**
 * Расчёт рецепта электрической доменной печи GT5-II (GT_MetaTileEntity_ElectricBlastFurnace#checkRecipe), без Minecraft.
 *
 * Правила оригинала:
 *  - уровень = уровень суммы напряжений энергетических люков (не ниже LV): два люка LV дают 64 В → уровень MV;
 *  - рецепт подходит, если его EU/t ≤ V уровня и нагрев катушек ≥ температуры рецепта;
 *  - рецепт до 16 EU/t: ×4^(уровень−1) к потреблению, ÷2^(уровень−1) ко времени;
 *    иначе: пока EU/t ≤ V предыдущего уровня — ×4 потребление, ÷2 время (÷4, если запас нагрева ≥ 900·i K);
 *  - каждые 900 K запаса нагрева сверх рецепта — ×0,95 к потреблению;
 *  - энергия за тик = EU/t × 10000 / max(1000, КПД).
 */
public final class BlastLogic {
    private BlastLogic() {}

    public static final long[] V = {8, 32, 128, 512, 2048, 8192, 32768, 131072, 524288, Integer.MAX_VALUE};

    /** GT_Utility#getTier. */
    public static int tier(long voltage) {
        int t = 0;
        while (t < V.length - 1 && voltage > V[t]) t++;
        return t;
    }

    public static int machineTier(long hatchVoltageSum) {
        return Math.max(1, tier(hatchVoltageSum));
    }

    public record Run(int eut, int duration) {}

    public static Run overclock(int recipeEut, int recipeDuration, int tier, int heat, int recipeTemp) {
        int heatDiv = (heat - recipeTemp) / 900;
        long eut;
        long dur;
        if (recipeEut <= 16) {
            long f = 1L << (tier - 1);
            eut = recipeEut * f * f;
            dur = recipeDuration / f;
        } else {
            eut = recipeEut;
            dur = recipeDuration;
            int i = 2;
            while (eut <= V[tier - 1]) {
                eut *= 4;
                dur /= heatDiv >= i ? 4 : 2;
                i += 2;
            }
        }
        if (heatDiv > 0) eut = (long) (eut * Math.pow(0.95, heatDiv));
        return new Run((int) eut, (int) Math.max(1, dur));
    }

    /** Энергия, снимаемая с люков за тик работы. */
    public static long drainPerTick(int eut, int efficiency) {
        return (long) eut * 10000 / Math.max(1000, efficiency);
    }

    /** Глушитель копит 5 за тик (EBF getPollutionPerTick) и выпускает порциями по 10000. */
    public static final int POLLUTION_PER_TICK = 5;
}
