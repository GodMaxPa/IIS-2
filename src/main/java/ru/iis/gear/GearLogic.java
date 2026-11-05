package ru.iis.gear;

/**
 * Расчёты снаряжения без Minecraft (D118). IC2 закрыт (D5): числа — из памяти о том, как он играется, кроме ёмкостей,
 * а те из scripts/GT_IC2.zs ИИС 1. Что именно из памяти — в docs/PORTING_NOTES.md.
 */
public final class GearLogic {
    private GearLogic() {}

    public static final long NANO_EU_PER_DAMAGE = 5000, QUANTUM_EU_PER_DAMAGE = 20000;
    public static final double NANO_RATIO = 0.9, QUANTUM_RATIO = 1.0;
    public static final long JETPACK_EU_PER_TICK = 8;
    /** Шлем, кираса, поножи, ботинки: доля урона, которую берёт деталь. Вместе 1.0. */
    private static final double[] BASE = {0.15, 0.40, 0.30, 0.15};

    public static double baseAbsorption(int piece) {
        return BASE[piece];
    }

    /** Итог поглощения: сколько урона осталось и сколько EU списать с каждой детали. */
    public record Absorb(float left, long[] used) {}

    /**
     * Каждая деталь берёт свою долю, но не больше, чем хватает заряда.
     * @param ratio доля детали (0 — детали нет)
     * @param charge заряд детали
     * @param euPerDamage цена единицы урона
     */
    public static Absorb absorb(float damage, double[] ratio, long[] charge, long[] euPerDamage) {
        long[] used = new long[ratio.length];
        double left = damage;
        for (int i = 0; i < ratio.length; i++) {
            if (ratio[i] <= 0 || euPerDamage[i] <= 0) continue;
            double share = Math.min(damage * ratio[i], (double) charge[i] / euPerDamage[i]);
            if (share <= 0) continue;
            left -= share;
            used[i] = (long) Math.ceil(share * euPerDamage[i]);
            used[i] = Math.min(used[i], charge[i]);
        }
        return new Absorb((float) Math.max(0, left), used);
    }

    /** Ботинки гасят падение: первые 10 блоков даром, дальше за EU. Вернёт цену; -1 — не хватает заряда. */
    public static long fallCost(double distance, long euPerDamage, long charge) {
        long cost = Math.max((long) distance - 10, 0) * euPerDamage;
        return cost <= charge ? cost : -1;
    }

    /**
     * Сила тяги ранца: 0,7; ниже 5 % заряда слабеет; у потолка полёта (70 % высоты мира) плавно гаснет в последних 25 блоках.
     * @return 0 — тяги нет
     */
    public static double jetPower(long charge, long capacity, double y, double minY, double height) {
        if (charge <= 0) return 0;
        double power = 0.7;
        double level = (double) charge / capacity;
        if (level <= 0.05) power *= level / 0.05;
        double ceiling = minY + height * 0.7;
        if (y >= ceiling) return 0;
        if (y > ceiling - 25) power *= (ceiling - y) / 25;
        return power;
    }

    public static double jetMotion(double motionY, double power) {
        return Math.min(motionY + power * 0.2, 0.6);
    }

    /** Режимы лазера: цена выстрела, дальность, сила (стойкость блоков вычитается из неё), лучи, плавка. */
    public enum LaserMode {
        MINING("mining", 1250, 128, 5, 1, false, false),
        LOW_FOCUS("low_focus", 100, 4, 5, 1, false, false),
        LONG_RANGE("long_range", 5000, 128, 20, 1, false, false),
        HORIZONTAL("horizontal", 3000, 128, 5, 1, false, true),
        SUPER_HEAT("super_heat", 2500, 128, 8, 1, true, false),
        SCATTER("scatter", 10000, 32, 12, 25, false, false),
        THREE_BY_THREE("three", 7500, 128, 5, 9, false, false);

        public final String id;
        public final long cost;
        public final int range, rays;
        public final float power;
        public final boolean smelts, horizontal;

        LaserMode(String id, long cost, int range, float power, int rays, boolean smelts, boolean horizontal) {
            this.id = id;
            this.cost = cost;
            this.range = range;
            this.power = power;
            this.rays = rays;
            this.smelts = smelts;
            this.horizontal = horizontal;
        }

        public LaserMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static LaserMode of(int i) {
            return values()[Math.floorMod(i, values().length)];
        }
    }

    /** Сколько силы съедает блок. Камень 1, руда 2, обсидиан не пробить в обычном режиме. */
    public static float blockCost(float hardness) {
        return Math.max(0.5F, hardness / 1.5F);
    }
}
