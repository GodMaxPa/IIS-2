package ru.iis.multi.logic;

import java.util.function.IntPredicate;

/**
 * Большой котёл GT5-II (GT_MetaTileEntity_LargeBoiler и наследники Bronze/Steel/Titanium/TungstenSteel), без Minecraft.
 *
 * Правила оригинала:
 *  - топливо сгорает порциями по 20 тиков (LargeBoiler.defaultProgressChunk = 20 в MachineStats.cfg ИИС), остаток
 *    горения копится в запасе (progressTimeStash) и расходуется следующими порциями;
 *  - твёрдое топливо: время горения в печи / 80 тиков на штуку (остатки от деления копятся); дизельное — 8000 л на
 *    special × 4 тика, полужидкое — 8000 л на special × 16 тиков; время умножается на коэффициент котла (бронза ×2,
 *    сталь ×1,5, титан ×1,3, вольфрамосталь ×1,2);
 *  - пара за тик: EU/t × 2 × КПД; воды — 1 л на 160 л пара (сначала дистиллированная); нет воды — котёл взрывается;
 *  - КПД растёт после каждой порции на время порции × прирост котла (у дизеля ×4), разогрев бронзы — 31 с;
 *  - программная схема 1–25 в контроллере снижает выход на 25 EU/t (50 л/т пара) за единицу, не ниже 25 EU/t;
 *    топливо при этом горит дольше; при разогреве схема не действует.
 */
public final class LargeBoilerLogic {
    public enum Tier {
        /** GT_MetaTileEntity_LargeBoiler_Bronze: 400 EU/t, прирост КПД 16, время ×2. */
        BRONZE(400, 16, 200, 0),
        /** _Steel: 600, 12, ×1,5. */
        STEEL(600, 12, 150, 0),
        /** _Titanium: 800, 8, ×1,3; кислород 32 л за тик порции (MachineStats.cfg ИИС). */
        TITANIUM(800, 8, 130, 32),
        /** _TungstenSteel: 1000, 4, ×1,2; кислород 40. */
        TUNGSTENSTEEL(1000, 4, 120, 40);

        public final int eut, efficiencyIncrease, boostPercent, oxygenPerOperation;

        Tier(int eut, int efficiencyIncrease, int boostPercent, int oxygenPerOperation) {
            this.eut = eut;
            this.efficiencyIncrease = efficiencyIncrease;
            this.boostPercent = boostPercent;
            this.oxygenPerOperation = oxygenPerOperation;
        }

        /** GT runtimeBoost. */
        public int runtimeBoost(int ticks) {
            return ticks * boostPercent / 100;
        }

        /** Пар за штуку угля на полном ходу, л (описание GT: EU/t × 40 × boost(20) / 20). */
        public int steamPerCoal() {
            return eut * 40 * runtimeBoost(20) / 20;
        }
    }

    public static final int PROGRESS_CHUNK = 20;
    /** Порция жидкого топлива, л (tFluid.amount = 8000 в checkRecipe). */
    public static final int LIQUID_PORTION = 8000;
    /** Вода: 1 л на 160 л пара. */
    public static final float STEAM_PER_WATER = 160f;
    /** Твёрдое топливо: время горения в печи делится на 80. */
    public static final int FUEL_DIVIDER = 80;
    /** Твёрдое топливо «высшего сорта» (для кислородного режима): ≥ 1250 тиков порции на штуку. */
    public static final int SUPER_FUEL = 1250;

    public final Tier tier;
    public int circuitConfig, excessFuel, excessProjectedEU, progressTimeStash, lastFuelEfficiencyIncrease;
    public float water;
    public boolean solidSuperFuel, oxygenBoost;
    /** Итог запуска порции: время, EU/t, прирост КПД после неё. */
    public int maxProgress, eut, efficiencyIncrease;

    public LargeBoilerLogic(Tier tier) {
        this.tier = tier;
    }

    /** GT checkRecipe, первая ветка: в запасе есть целая порция. */
    public boolean fromStash(int efficiency, int maxEfficiency, IntPredicate oxygen) {
        if (progressTimeStash < PROGRESS_CHUNK) return false;
        progressTimeStash -= PROGRESS_CHUNK;
        maxProgress = PROGRESS_CHUNK;
        calc(efficiency, maxEfficiency, oxygen);
        return true;
    }

    /** Дизельное топливо (sDieselFuels, special > 1): 8000 л уже забраны. */
    public void fromDiesel(int special, int efficiency, int maxEfficiency, IntPredicate oxygen) {
        maxProgress = special * (LIQUID_PORTION / 1000) / 2;
        chunk();
        lastFuelEfficiencyIncrease = maxProgress * tier.efficiencyIncrease * 4;
        calc(efficiency, maxEfficiency, oxygen);
    }

    /** Полужидкое топливо (sDenseLiquidFuels): 8000 л уже забраны. */
    public void fromDense(int special, int efficiency, int maxEfficiency, IntPredicate oxygen) {
        maxProgress = Math.max(1, special * (LIQUID_PORTION / 1000) * 2);
        chunk();
        lastFuelEfficiencyIncrease = maxProgress * tier.efficiencyIncrease;
        calc(efficiency, maxEfficiency, oxygen);
    }

    /**
     * Твёрдое топливо: из стопки count штук со временем горения burn сжигаются штуки, пока не наберётся порция.
     * Возвращает, сколько штук сожжено (не меньше 1).
     */
    public int fromSolid(int burn, int count, int efficiency, int maxEfficiency, IntPredicate oxygen) {
        int fuelValue = burn / FUEL_DIVIDER, used = 0;
        maxProgress = 0;
        while (count - used > 0 && maxProgress < PROGRESS_CHUNK) {
            maxProgress += fuelValue;
            excessFuel += burn % FUEL_DIVIDER;
            maxProgress += excessFuel / FUEL_DIVIDER;
            excessFuel %= FUEL_DIVIDER;
            used++;
        }
        maxProgress += progressTimeStash;
        progressTimeStash = 0;
        chunk();
        lastFuelEfficiencyIncrease = maxProgress * tier.efficiencyIncrease;
        calc(efficiency, maxEfficiency, oxygen);
        return used;
    }

    /** Последняя ветка: топлива нет, догорает неполная порция из запаса. */
    public boolean fromLeftover(int efficiency, int maxEfficiency, IntPredicate oxygen) {
        if (progressTimeStash <= 0) return false;
        maxProgress = progressTimeStash;
        progressTimeStash = 0;
        calc(efficiency, maxEfficiency, oxygen);
        efficiencyIncrease = maxProgress * tier.efficiencyIncrease;
        return true;
    }

    private void chunk() {
        if (maxProgress >= PROGRESS_CHUNK) {
            progressTimeStash = maxProgress - PROGRESS_CHUNK;
            maxProgress = PROGRESS_CHUNK;
        }
    }

    /** GT calcProgressVars: кислород (титан, вольфрамосталь), прирост КПД, время и EU/t с учётом схемы. */
    private void calc(int efficiency, int maxEfficiency, IntPredicate oxygen) {
        oxygenBoost = tier.oxygenPerOperation > 0 && solidSuperFuel && oxygen.test(tier.oxygenPerOperation * maxProgress);
        efficiencyIncrease = lastFuelEfficiencyIncrease;
        maxProgress = adjustBurnTime(tier.runtimeBoost(maxProgress), efficiency, maxEfficiency);
        eut = adjustEUt(efficiency, maxEfficiency);
    }

    private int throttledEUt() {
        return Math.max(25, tier.eut - 25 * circuitConfig);
    }

    int adjustEUt(int efficiency, int maxEfficiency) {
        if (efficiency < maxEfficiency) return tier.eut;
        return throttledEUt();
    }

    int adjustBurnTime(int raw, int efficiency, int maxEfficiency) {
        if (efficiency < maxEfficiency) return raw;
        int adjustedEUt = throttledEUt();
        int adjusted = raw * tier.eut / adjustedEUt;
        excessProjectedEU += tier.eut * raw - adjustedEUt * adjusted;
        adjusted += excessProjectedEU / adjustedEUt;
        excessProjectedEU %= adjustedEUt;
        return adjusted;
    }

    /** GT onRunningTick: пар за тик при этом КПД (сотые доли процента). */
    public int steamPerTick(int efficiency) {
        return (int) (eut * 2L * efficiency / 10000L);
    }

    /** Сколько литров воды забрать за этот тик (дробная часть копится). */
    public int waterFor(int steam) {
        water += steam / STEAM_PER_WATER;
        int amount = (int) water;
        water -= amount;
        return amount;
    }

    /** GT getPollutionPerTick: 12 в тик на полном ходу, меньше при снижении схемой, ×3 с кислородом. */
    public int pollutionPerTick() {
        int adjusted = throttledEUt() * (oxygenBoost ? 3 : 1);
        return Math.max(1, 12 * adjusted / tier.eut);
    }

    /** Схема в контроллере: 1–25 — новая настройка; 0 — прежняя остаётся; нет схемы — 0. */
    public void readCircuit(int config, boolean present) {
        if (!present) circuitConfig = 0;
        else if (config >= 1 && config <= 25) circuitConfig = config;
    }
}
