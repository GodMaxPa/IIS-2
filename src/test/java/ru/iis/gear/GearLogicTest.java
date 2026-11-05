package ru.iis.gear;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GearLogicTest {
    private static double[] set(double ratio) {
        double[] r = new double[4];
        for (int i = 0; i < 4; i++) r[i] = GearLogic.baseAbsorption(i) * ratio;
        return r;
    }

    @Test
    void shares() {
        double s = 0;
        for (int i = 0; i < 4; i++) s += GearLogic.baseAbsorption(i);
        assertEquals(1.0, s, 1e-9);
    }

    @Test
    void fullQuantumTakesEverything() {
        long[] charge = {10_000_000, 10_000_000, 10_000_000, 10_000_000};
        long q = GearLogic.QUANTUM_EU_PER_DAMAGE;
        GearLogic.Absorb a = GearLogic.absorb(10, set(GearLogic.QUANTUM_RATIO), charge, new long[]{q, q, q, q});
        assertEquals(0, a.left(), 1e-4);
        assertEquals(80000, a.used()[1]);
    }

    @Test
    void fullNanoLeavesTenPercent() {
        long[] charge = {1_000_000, 1_000_000, 1_000_000, 1_000_000};
        long n = GearLogic.NANO_EU_PER_DAMAGE;
        GearLogic.Absorb a = GearLogic.absorb(10, set(GearLogic.NANO_RATIO), charge, new long[]{n, n, n, n});
        assertEquals(1, a.left(), 1e-4);
    }

    @Test
    void emptyPieceDoesNotAbsorb() {
        long n = GearLogic.NANO_EU_PER_DAMAGE;
        GearLogic.Absorb a = GearLogic.absorb(10, set(GearLogic.NANO_RATIO), new long[4], new long[]{n, n, n, n});
        assertEquals(10, a.left(), 1e-4);
    }

    @Test
    void lowChargeAbsorbsWhatItCan() {
        long n = GearLogic.NANO_EU_PER_DAMAGE;
        GearLogic.Absorb a = GearLogic.absorb(10, new double[]{0, 0.9, 0, 0}, new long[]{0, 5000, 0, 0}, new long[]{n, n, n, n});
        assertEquals(9, a.left(), 1e-4);
        assertEquals(5000, a.used()[1]);
    }

    @Test
    void fallFreeUpToTenBlocks() {
        assertEquals(0, GearLogic.fallCost(10, 5000, 0));
        assertEquals(5000 * 10, GearLogic.fallCost(20, 5000, 100000));
        assertEquals(-1, GearLogic.fallCost(20, 5000, 100));
    }

    @Test
    void jetpackFadesAtCeilingAndWhenEmpty() {
        assertEquals(0.7, GearLogic.jetPower(30000, 30000, 64, -64, 384), 1e-9);
        assertEquals(0, GearLogic.jetPower(0, 30000, 64, -64, 384));
        assertEquals(0, GearLogic.jetPower(30000, 30000, 300, -64, 384));
        assertTrue(GearLogic.jetPower(30000, 30000, 195, -64, 384) < 0.7);
        assertTrue(GearLogic.jetPower(1000, 30000, 64, -64, 384) < 0.7);
    }

    @Test
    void jetMotionCapped() {
        assertEquals(0.6, GearLogic.jetMotion(0.55, 0.7), 1e-9);
        assertEquals(0.14, GearLogic.jetMotion(0, 0.7), 1e-9);
    }

    @Test
    void laserModesCycle() {
        assertEquals(GearLogic.LaserMode.LOW_FOCUS, GearLogic.LaserMode.MINING.next());
        assertEquals(GearLogic.LaserMode.MINING, GearLogic.LaserMode.THREE_BY_THREE.next());
        assertEquals(GearLogic.LaserMode.MINING, GearLogic.LaserMode.of(7));
    }

    @Test
    void blockCost() {
        assertEquals(1, GearLogic.blockCost(1.5F), 1e-6);
        assertEquals(2, GearLogic.blockCost(3F), 1e-6);
        assertTrue(GearLogic.blockCost(50F) > GearLogic.LaserMode.LONG_RANGE.power);
    }
}
