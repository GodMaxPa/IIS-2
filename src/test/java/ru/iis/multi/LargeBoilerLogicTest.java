package ru.iis.multi;

import org.junit.jupiter.api.Test;
import ru.iis.multi.logic.LargeBoilerLogic;
import ru.iis.multi.logic.LargeBoilerLogic.Tier;
import ru.iis.multi.logic.Maintenance;

import java.util.Random;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/** Большой котёл GT5-II (GT_MetaTileEntity_LargeBoiler): числа оригинала. */
class LargeBoilerLogicTest {
    private static final IntPredicate NO_OXYGEN = a -> false;
    private static final int FULL = 10000;

    @Test
    void descriptionNumbers() {
        assertEquals(32000, Tier.BRONZE.steamPerCoal());
        assertEquals(36000, Tier.STEEL.steamPerCoal());
        assertEquals(41600, Tier.TITANIUM.steamPerCoal());
        assertEquals(48000, Tier.TUNGSTENSTEEL.steamPerCoal());
    }

    @Test
    void coldBronzeBurnsOneCoalForFortyTicks() {
        LargeBoilerLogic l = new LargeBoilerLogic(Tier.BRONZE);
        assertEquals(1, l.fromSolid(1600, 64, 0, FULL, NO_OXYGEN));
        assertEquals(40, l.maxProgress);          // 1600 / 80 = 20 тиков, бронза ×2
        assertEquals(400, l.eut);                 // при разогреве схема не действует
        assertEquals(320, l.efficiencyIncrease);  // 20 × 16
        assertEquals(0, l.progressTimeStash);
        assertEquals(800, l.steamPerTick(FULL));
        assertEquals(400, l.steamPerTick(5000));
    }

    @Test
    void heatUpTakesAboutSixtyFourSeconds() {
        // бронза: 32 порции по 40 тиков (+320 КПД за порцию), сталь: 42 по 30 (+240)
        for (Tier t : new Tier[]{Tier.BRONZE, Tier.STEEL}) {
            LargeBoilerLogic l = new LargeBoilerLogic(t);
            int eff = 0, ticks = 0;
            while (eff < FULL) {
                l.fromSolid(1600, 64, eff, FULL, NO_OXYGEN);
                ticks += l.maxProgress;
                eff = Math.min(FULL, eff + l.efficiencyIncrease);
            }
            assertEquals(t == Tier.BRONZE ? 1280 : 1260, ticks, t.name());
        }
    }

    @Test
    void planksAreBurntUntilAPortion() {
        LargeBoilerLogic l = new LargeBoilerLogic(Tier.BRONZE);
        // 300 / 80 = 3 тика и 60 в остаток на каждую доску: 3, 7, 11, 15, 18, 22 — шесть досок, 2 тика в запас
        assertEquals(6, l.fromSolid(300, 10, 0, FULL, NO_OXYGEN));
        assertEquals(2, l.progressTimeStash);
        assertEquals(40, l.maxProgress);
        assertEquals(40, l.excessFuel);
    }

    @Test
    void circuitThrottlesHotBoiler() {
        LargeBoilerLogic l = new LargeBoilerLogic(Tier.BRONZE);
        l.readCircuit(4, true);
        l.fromSolid(1600, 1, FULL, FULL, NO_OXYGEN);
        assertEquals(300, l.eut);                 // 400 − 4 × 25
        assertEquals(53, l.maxProgress);          // 40 × 400 / 300, остаток 100 EU копится
        assertEquals(100, l.excessProjectedEU);
        assertEquals(9, l.pollutionPerTick());    // 12 × 300 / 400
        l.readCircuit(0, true);                   // схема 0 настройку не меняет
        assertEquals(4, l.circuitConfig);
        l.readCircuit(0, false);
        assertEquals(0, l.circuitConfig);
        l.readCircuit(25, true);
        l.fromSolid(1600, 1, FULL, FULL, NO_OXYGEN);
        assertEquals(25, l.eut);                  // не ниже 25 EU/t
        assertEquals(1, l.pollutionPerTick());
    }

    @Test
    void liquidFuels() {
        LargeBoilerLogic l = new LargeBoilerLogic(Tier.BRONZE);
        l.fromDiesel(256, 0, FULL, NO_OXYGEN);   // топливо: 8000 л → 256 × 4 тиков
        assertEquals(1004, l.progressTimeStash);
        assertEquals(40, l.maxProgress);
        assertEquals(1280, l.efficiencyIncrease); // 20 × 16 × 4
        l = new LargeBoilerLogic(Tier.BRONZE);
        l.fromDense(8, 0, FULL, NO_OXYGEN);      // креозот: 8 × 16
        assertEquals(108, l.progressTimeStash);
        assertEquals(320, l.efficiencyIncrease);
    }

    @Test
    void stashFeedsPortionsThenLeftover() {
        LargeBoilerLogic l = new LargeBoilerLogic(Tier.BRONZE);
        l.progressTimeStash = 25;
        assertTrue(l.fromStash(0, FULL, NO_OXYGEN));
        assertEquals(5, l.progressTimeStash);
        assertEquals(40, l.maxProgress);
        assertFalse(l.fromStash(0, FULL, NO_OXYGEN));
        l.lastFuelEfficiencyIncrease = 320;
        assertTrue(l.fromLeftover(0, FULL, NO_OXYGEN));
        assertEquals(10, l.maxProgress);
        assertEquals(160, l.efficiencyIncrease);  // время после ×2 × 16
        assertFalse(l.fromLeftover(0, FULL, NO_OXYGEN));
    }

    @Test
    void waterIsOneLitrePer160Steam() {
        LargeBoilerLogic l = new LargeBoilerLogic(Tier.BRONZE);
        assertEquals(5, l.waterFor(800));
        assertEquals(0, l.waterFor(80));          // половина литра копится
        assertEquals(1, l.waterFor(80));
    }

    @Test
    void oxygenOnlyWithSuperFuelOnTitanium() {
        LargeBoilerLogic l = new LargeBoilerLogic(Tier.TITANIUM);
        int[] asked = {0};
        l.solidSuperFuel = true;
        l.fromSolid(100000, 1, 0, FULL, a -> { asked[0] = a; return true; });
        assertTrue(l.oxygenBoost);
        assertEquals(32 * 20, asked[0]);
        assertEquals(36, l.pollutionPerTick());
        LargeBoilerLogic b = new LargeBoilerLogic(Tier.BRONZE);
        b.solidSuperFuel = true;
        b.fromSolid(100000, 1, 0, FULL, a -> true);
        assertFalse(b.oxygenBoost);
    }

    @Test
    void wearRunsEveryThousandTicks() {
        Maintenance m = new Maintenance();
        for (Maintenance.Problem p : Maintenance.Problem.values()) m.fix(p);
        int[] wear = {0};
        Random r = new Random(3);
        for (int t = 0; t < 3003; t++) m.randomDamage(r::nextInt, () -> wear[0]++);
        assertEquals(2, wear[0]);
    }
}
