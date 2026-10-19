package ru.iis.pollution;

/**
 * Загрязнение GT5-II (gregtech.common.GT_Pollution), без Minecraft.
 *
 * Правила оригинала:
 *  - загрязнение хранится по чанкам; все чанки с данными обходятся раз в 1200 тиков (равномерно по тикам);
 *  - при обходе: ×0,99 и −2000 (не ниже 0);
 *  - выше 50000 — растекается в 4 соседних чанка: если сосед × 6 < своё × 5, то соседу уходит (своё − сосед) / 10;
 *  - эффекты (только выше 50000): смог, отравление, гибель растений, кислотные дожди — по порогам из GregTech.cfg ИИС.
 */
public final class PollutionLogic {
    private PollutionLogic() {}

    /** Длина цикла обхода, тики. */
    public static final int CYCLE = 1200;
    /** Порог растекания и эффектов. */
    public static final int SPREAD_THRESHOLD = 50000;

    /** ИИС GregTech.cfg [pollution]. */
    public static final int SMOG = 500000, POISON = 750000, VEGETATION = 1000000, SOUR_RAIN = 2000000;

    /** Источники GT5-II, единиц в секунду: котлы и кирпичная доменная печь. */
    public static final int BOILER_BRONZE = 20, BOILER_STEEL = 30, BOILER_LAVA = 20, BRICKED_BLAST_FURNACE = 50;
    /** Генераторы: за каждое сжигание (раз в 10 тиков) 10 × базовое; дизель LV — 2, газовая турбина LV — 1. */
    public static final int DIESEL_BASE = 2, GAS_TURBINE_BASE = 1;
    /** Взрыв машины (BaseMetaTileEntity#doExplosion). */
    public static final int MACHINE_EXPLOSION = 100000;
    /** Порция, которую глушитель выпускает за раз. */
    public static final int MUFFLER_PORTION = 10000;

    public static int decay(int p) {
        p = (int) (0.99f * p) - 2000;
        return Math.max(0, p);
    }

    /** Растекание: меняет соседей, возвращает остаток в своём чанке. */
    public static int spread(int p, int[] neighbours) {
        if (p <= SPREAD_THRESHOLD) return p;
        for (int i = 0; i < neighbours.length; i++) {
            int n = neighbours[i];
            if ((long) n * 6 < (long) p * 5) {
                int d = (p - n) / 10;
                neighbours[i] = n + d;
                p -= d;
            }
        }
        return p;
    }

    /** Глушитель уровня tier (LV = 1) выпускает порцию × 0,7^(tier−1): LV 100 %, MV 70 %, HV 49 %… */
    public static int mufflerOutput(int portion, int tier) {
        return (int) (portion * Math.pow(0.7, tier - 1));
    }

    /** Сколько попыток повредить растения за обход (GT: f от 20 до p/25000). */
    public static int vegetationAttempts(int p) {
        return Math.max(0, p / 25000 - 20);
    }
}
