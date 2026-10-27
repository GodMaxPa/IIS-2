package ru.iis.multi.logic;

/**
 * Большая паровая турбина GT5-II (GT_MetaTileEntity_LargeTurbine + _Steam) и её роторы (GT_Tool_Turbine_*), без Minecraft.
 *
 * Правила оригинала:
 *  - ротор задаёт оптимальный поток (скорость размера × скорость материала × 50 л/т) и КПД (50 % + 10 % × (урон размера
 *    + качество материала));
 *  - за тик турбина берёт до 125 % оптимального потока; EU = min(поток, оптимум) × КПД / 2; меньше оптимума — ещё и
 *    штраф 1 − |поток − оптимум| / оптимум (больше оптимума — без штрафа, но лишний пар уходит впустую);
 *  - выход меняется плавно: не больше чем на max(10, 1 % разницы) EU/t за тик; «скорость» (КПД машины) растёт на 0,1 % за тик;
 *  - из пара получается дистиллированная вода: 1 л на 160 л;
 *  - раз в 1000 тиков работы с шансом 1/2 ротор изнашивается на min(EU/t / 5, EU/t ^ 0,6).
 */
public final class LargeTurbineLogic {
    private LargeTurbineLogic() {}

    public enum Size {
        /** GT_MetaGenerated_Tool_01 TURBINE_SMALL 170: GT_Tool_Turbine_Small. */
        SMALL(1.0f, 1.0f, 0.0f),
        /** TURBINE 172. */
        NORMAL(2.0f, 2.0f, 2.5f),
        /** TURBINE_LARGE 174. */
        LARGE(3.0f, 3.0f, 5.0f),
        /** TURBINE_HUGE 176. */
        HUGE(4.0f, 4.0f, 3.0f);

        public final float speedMultiplier, durabilityMultiplier, baseDamage;

        Size(float speedMultiplier, float durabilityMultiplier, float baseDamage) {
            this.speedMultiplier = speedMultiplier;
            this.durabilityMultiplier = durabilityMultiplier;
            this.baseDamage = baseDamage;
        }
    }

    /** MachineStats.cfg ИИС: MultiBlockMachines.damageFactorLow = 5, damageFactorHigh = 0.6. */
    public static final int DAMAGE_FACTOR_LOW = 5;
    public static final float DAMAGE_FACTOR_HIGH = 0.6f;

    /** GT checkRecipe: baseEff = (50 + 10 × getToolCombatDamage) × 100, в сотых долях процента. */
    public static int baseEfficiency(Size s, int toolQuality) {
        return (int) ((50.0f + 10.0f * (s.baseDamage + toolQuality)) * 100);
    }

    /** GT checkRecipe: optFlow = скорость размера × mToolSpeed × 50, л/т. */
    public static int optimalFlow(Size s, float toolSpeed) {
        return (int) Math.max(Float.MIN_NORMAL, s.speedMultiplier * toolSpeed * 50);
    }

    /** GT getToolWithStats: MaxDamage = 100 × (long) (durability × множитель размера). */
    public static long maxDamage(Size s, int durability) {
        return 100L * (long) (durability * s.durabilityMultiplier);
    }

    /** GT doRandomMaintenanceDamage: износ ротора за раз. */
    public static long wear(int eut) {
        return (long) Math.min(eut / DAMAGE_FACTOR_LOW, Math.pow(eut, DAMAGE_FACTOR_HIGH));
    }

    /** GT getInfoData: «Turbine Damage» — износ в процентах (+1). */
    public static int damagePercent(long damage, long maxDamage) {
        return maxDamage <= 0 ? 0 : (int) ((100.0f / maxDamage * damage) + 1);
    }

    /** Сколько пара турбина может забрать за тик: 125 % оптимума. */
    public static int flowLimit(int optFlow) {
        return (int) (optFlow * 1.25f);
    }

    /** GT_MetaTileEntity_LargeTurbine_Steam#fluidIntoPower: EU/t, к которым стремится турбина при этом потоке пара. */
    public static int steamPower(int totalFlow, int optFlow, int baseEff) {
        int tEU = Math.min(optFlow, totalFlow);
        if (totalFlow > 0 && totalFlow != optFlow) {
            float efficiency = 1.0f - Math.abs((totalFlow - (float) optFlow) / optFlow);
            if (totalFlow > optFlow) efficiency = 1.0f;
            tEU *= efficiency;
            tEU = Math.max(1, (int) ((long) tEU * (long) baseEff / 20000L));
        } else {
            tEU = (int) ((long) tEU * (long) baseEff / 20000L);
        }
        return tEU;
    }

    /** GT checkRecipe: выход меняется не больше чем на max(10, ⌈1 % разницы⌉) EU/t за тик. */
    public static int approach(int current, int target) {
        int difference = target - current;
        int maxChange = Math.max(10, (int) Math.ceil(Math.abs(difference) * 0.01));
        if (Math.abs(difference) > maxChange) return current + maxChange * (difference > 0 ? 1 : -1);
        return target;
    }

    /** GT realOptFlow (для экрана): (optFlow / 2) / 0,5. */
    public static int shownOptimalFlow(int optFlow) {
        return (int) ((optFlow / 2) / 0.5);
    }
}
