package ru.iis.machine.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Числа из GT_MetaTileEntity_Boiler_* (GT5-II @ 00021e3). */
class BoilerLogicTest {
    /** Бесконечный уголь (160 энергии), пар никуда не уходит. */
    static class Coal implements BoilerLogic.Env {
        int burned;

        @Override public void pushSteam() {}

        @Override public int refuel() {
            burned++;
            return 160;
        }
    }

    private static long run(BoilerLogic b, BoilerLogic.Env env, long from, long ticks) {
        for (long t = from; t < from + ticks; t++) {
            assertNotEquals(BoilerLogic.Result.EXPLODE, b.tick(t, env), "explosion at tick " + t);
        }
        return from + ticks;
    }

    @Test
    void bronzeHeatsUpAndMakes120LitersPerSecond() {
        BoilerLogic b = new BoilerLogic(BoilerLogic.Kind.COAL_BRONZE);
        b.water = 16000;
        Coal env = new Coal();
        long t = run(b, env, 0, 20 * 60 * 10); // нагрев 20→500 занимает около 6,5 минуты
        assertEquals(500, b.temperature, 2, "bronze boiler tops out at 500");
        b.steam = 0;
        int steam0 = b.steam, water0 = b.water;
        run(b, env, t, 20 * 50); // 50 секунд
        assertEquals(120 * 50, b.steam - steam0, "120 L/s");
        assertEquals(40, water0 - b.water, "1 L of water per 150 L of steam");
    }

    @Test
    void steelMakes300LitersPerSecond() {
        BoilerLogic b = new BoilerLogic(BoilerLogic.Kind.COAL_STEEL);
        b.water = 32000;
        b.temperature = 1000;
        Coal env = new Coal();
        run(b, env, 0, 20 * 10);
        assertEquals(300 * 10, b.steam);
    }

    @Test
    void fuelIsSpentOnlyWhileHeating() {
        // Особенность GT5: на пределе температуры котёл тратит топливо только на компенсацию остывания
        BoilerLogic b = new BoilerLogic(BoilerLogic.Kind.COAL_BRONZE);
        b.water = 16000;
        b.temperature = 500;
        Coal env = new Coal();
        run(b, env, 0, 20 * 60 * 10); // 10 минут
        // остывание 1 градус в 46 тиков => ~260 шагов нагрева => ~2 единицы угля
        assertTrue(env.burned <= 3, "burned " + env.burned);
    }

    @Test
    void addingWaterToHotDryBoilerExplodes() {
        BoilerLogic b = new BoilerLogic(BoilerLogic.Kind.COAL_BRONZE);
        b.temperature = 300;
        b.energy = 1000;
        Coal env = new Coal();
        long t = 0;
        for (; t < 100; t++) assertEquals(BoilerLogic.Result.NONE, b.tick(t, env));
        assertTrue(b.hadNoWater);
        b.water = 1000;
        BoilerLogic.Result r = BoilerLogic.Result.NONE;
        for (; t < 200 && r != BoilerLogic.Result.EXPLODE; t++) r = b.tick(t, env);
        assertEquals(BoilerLogic.Result.EXPLODE, r);
    }

    @Test
    void coolBoilerForgetsDryState() {
        BoilerLogic b = new BoilerLogic(BoilerLogic.Kind.COAL_BRONZE);
        b.temperature = 200;
        Coal env = new Coal() {
            @Override public int refuel() { return 0; }
        };
        long t = run(b, env, 0, 100);
        assertTrue(b.hadNoWater);
        b.temperature = 50;
        t = run(b, env, t, 50);
        assertFalse(b.hadNoWater, "below 100 degrees the danger is reset");
        b.water = 1000;
        run(b, env, t, 100);
    }

    @Test
    void overflowResetsToThreeQuarters() {
        BoilerLogic b = new BoilerLogic(BoilerLogic.Kind.COAL_BRONZE);
        b.steam = 16001;
        assertEquals(BoilerLogic.Result.OVERFLOW, b.tick(1, new Coal()));
        assertEquals(12000, b.steam);
    }

    @Test
    void solarCalcifies() {
        assertEquals(150, BoilerLogic.solarOutput(43200));
        assertEquals(149, BoilerLogic.solarOutput(43200 + 288));
        assertEquals(50, BoilerLogic.solarOutput(43200 + 288 * 100));
        assertEquals(50, BoilerLogic.solarOutput(Integer.MAX_VALUE / 2));
        assertEquals(40, BoilerLogic.solarEnergy(true, false, false, false, true));
        assertEquals(5, BoilerLogic.solarEnergy(true, false, false, false, false));
        assertEquals(0, BoilerLogic.solarEnergy(false, false, false, false, true));
        assertEquals(0, BoilerLogic.solarEnergy(true, true, true, false, true));
    }

    @Test
    void lavaEfficiency() {
        assertEquals(300, BoilerLogic.lavaSteam(BoilerLogic.lavaEfficiency(0, 64)));
        assertEquals(100, BoilerLogic.lavaSteam(BoilerLogic.lavaEfficiency(64, 64)));
        assertEquals(200, BoilerLogic.lavaSteam(BoilerLogic.lavaEfficiency(32, 64)));
    }
}
