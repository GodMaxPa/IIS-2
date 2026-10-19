package ru.iis.machine.logic;

import org.junit.jupiter.api.Test;
import ru.iis.energy.logic.EnergyStorage;

import static org.junit.jupiter.api.Assertions.*;

class ElectricMachineLogicTest {
    static class Env implements ElectricMachineLogic.Env {
        int starts, finished;
        int[] recipe = {2, 400};
        @Override public int[] tryStart() { starts++; return recipe; }
        @Override public void finish() { finished++; }
    }

    @Test
    void lvNumbers() {
        ElectricMachineLogic m = new ElectricMachineLogic(32);
        assertEquals(2048, m.capacity());
        assertEquals(512, m.minimumStored());
        assertEquals(1, m.maxAmperesIn(), "idle");
        m.eut = 30;
        assertEquals(2, m.maxAmperesIn(), "(30*2-1)/32+1");
    }

    @Test
    void needsHalfMinimumToStartAndRuns() {
        ElectricMachineLogic m = new ElectricMachineLogic(32);
        EnergyStorage e = new EnergyStorage(2048, 32);
        Env env = new Env();
        e.stored = 255;
        m.tick(true, e, env);
        assertEquals(0, env.starts);
        e.stored = 2048;
        m.tick(true, e, env);
        assertEquals(1, env.starts);
        for (int i = 0; i < 400; i++) m.tick(false, e, env);
        assertEquals(1, env.finished);
        assertEquals(2048 - 800, e.stored);
    }

    @Test
    void outOfEnergyStutters() {
        ElectricMachineLogic m = new ElectricMachineLogic(32);
        EnergyStorage e = new EnergyStorage(2048, 32);
        Env env = new Env();
        env.recipe = new int[]{30, 400};
        e.stored = 300;
        m.tick(true, e, env);
        for (int i = 0; i < 20; i++) m.tick(false, e, env);
        assertTrue(m.stuttering);
        assertTrue(m.progress < 0);
    }
}
