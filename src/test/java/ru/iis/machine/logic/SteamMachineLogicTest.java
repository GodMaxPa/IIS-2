package ru.iis.machine.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** GT_MetaTileEntity_BasicMachine(_Bronze): запуск, расход пара, заикание, выпуск пара. */
class SteamMachineLogicTest {
    static class Env implements SteamMachineLogic.Env {
        boolean clear = true;
        int vents, finished, starts;
        int[] recipe = {2, 800}; // бронзовый дробитель: 2 EU/t, 400×2 тиков

        @Override public boolean ventClear() { return clear; }
        @Override public void vent() { vents++; }
        @Override public int[] tryStart() { starts++; return recipe; }
        @Override public void finish() { finished++; }
    }

    @Test
    void steamUnitsAreHalfLiters() {
        SteamMachineLogic m = new SteamMachineLogic();
        assertEquals(1000, m.fillSteam(1001));
        assertEquals(500, m.steamUnits);
        assertEquals(0, m.fillSteam(1));
        m.steamUnits = SteamMachineLogic.MAX_STEAM_UNITS - 10;
        assertEquals(20, m.fillSteam(1000));
    }

    @Test
    void needs500UnitsToStart() {
        SteamMachineLogic m = new SteamMachineLogic();
        Env env = new Env();
        m.steamUnits = 499;
        m.tick(true, env);
        assertEquals(0, env.starts);
        m.steamUnits = 500;
        m.tick(true, env);
        assertEquals(1, env.starts);
        assertEquals(800, m.maxProgress);
    }

    @Test
    void fullCycleConsumesEutPerTickAndVents() {
        SteamMachineLogic m = new SteamMachineLogic();
        Env env = new Env();
        m.steamUnits = 16000;
        m.tick(true, env);
        for (int i = 0; i < 800; i++) m.tick(false, env);
        assertEquals(1, env.finished);
        assertEquals(16000 - 2 * 800, m.steamUnits);
        assertEquals(1, env.vents, "vented right after the operation");
        assertEquals(2, env.starts, "next operation starts immediately after venting");
    }

    @Test
    void blockedVentStopsTheMachine() {
        SteamMachineLogic m = new SteamMachineLogic();
        Env env = new Env();
        env.recipe = new int[]{2, 10};
        m.steamUnits = 16000;
        m.tick(true, env);
        env.clear = false;
        for (int i = 0; i < 100; i++) m.tick(true, env);
        assertEquals(1, env.finished);
        assertEquals(1, env.starts, "no new recipe while the vent is blocked");
        assertTrue(m.needsVenting);
        env.clear = true;
        m.tick(true, env);
        assertEquals(1, env.vents);
        assertEquals(2, env.starts);
    }

    @Test
    void runningOutOfSteamStutters() {
        SteamMachineLogic m = new SteamMachineLogic();
        Env env = new Env();
        env.recipe = new int[]{100, 100};
        m.steamUnits = 600;
        m.tick(true, env); // старт
        for (int i = 0; i < 6; i++) m.tick(false, env); // 600 единиц = 6 тиков
        m.tick(false, env);
        assertTrue(m.stuttering);
        assertEquals(-SteamMachineLogic.STUTTER_TICKS, m.progress);
        m.steamUnits = 16000;
        for (int i = 0; i < 100; i++) m.tick(false, env);
        assertEquals(0, m.progress, "100 idle ticks, progress lost");
        assertEquals(16000, m.steamUnits, "no steam used while stuttering");
    }

    @Test
    void tiers() {
        assertArrayEquals(new int[]{2, 800}, SteamMachineLogic.scale(false, 2, 400));
        assertArrayEquals(new int[]{6, 400}, SteamMachineLogic.scale(true, 2, 400));
        assertArrayEquals(new int[]{4, 256}, SteamMachineLogic.scale(false, 4, 128), "bronze furnace");
        assertArrayEquals(new int[]{12, 128}, SteamMachineLogic.scale(true, 4, 128), "steel furnace");
    }
}
