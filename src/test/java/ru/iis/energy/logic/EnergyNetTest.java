package ru.iis.energy.logic;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Передача по проводам GT5-II: потери, ампераж, перегрев, взрыв от перенапряжения. */
class EnergyNetTest {
    /** Линия по оси X: x=0 источник, далее провода, в конце машина. Грань 4 — −X, 5 — +X. */
    static final class Line implements EnergyNet.Graph<Integer> {
        final int length;
        final CableSpec spec;
        final Map<Integer, CableState> states = new HashMap<>();
        final Map<Integer, EnergyStorage> sinks = new HashMap<>();
        boolean exploded;
        long tick;

        Line(int cables, CableSpec spec) {
            this.length = cables;
            this.spec = spec;
        }

        @Override public Integer neighbour(Integer n, int face) {
            int x = n + (face == 5 ? 1 : face == 4 ? -1 : 0);
            if (face < 4 || x < 0 || x > length + 1) return null;
            return x;
        }

        @Override public boolean isCable(Integer n) { return n >= 1 && n <= length; }
        @Override public CableSpec spec(Integer c) { return spec; }
        @Override public CableState state(Integer c) { return states.computeIfAbsent(c, k -> new CableState()); }
        @Override public boolean connected(Integer c, int face) { return face == 4 || face == 5; }

        @Override public long insert(Integer sink, int face, long voltage, long amps) {
            EnergyStorage s = sinks.get(sink);
            if (s == null) return 0;
            EnergyStorage.Inject r = s.inject(tick, voltage, amps);
            if (r.overvoltage()) exploded = true;
            return r.amperes();
        }
    }

    static final CableSpec TIN = new CableSpec(32, 1, 1, true);

    @Test
    void lossPerBlockReducesVoltage() {
        Line l = new Line(5, TIN);
        EnergyStorage m = new EnergyStorage(2048, 32);
        l.sinks.put(6, m);
        long used = EnergyNet.emit(l, 0, new int[]{5}, 32, 1, 0);
        assertEquals(1, used);
        assertEquals(27, m.stored, "32 V minus 5 blocks × 1");
    }

    @Test
    void voltageCanDieOnLongCables() {
        Line l = new Line(40, TIN);
        EnergyStorage m = new EnergyStorage(2048, 32);
        l.sinks.put(41, m);
        assertEquals(0, EnergyNet.emit(l, 0, new int[]{5}, 32, 1, 0));
        assertEquals(0, m.stored);
    }

    @Test
    void overvoltageExplodesMachine() {
        Line l = new Line(1, new CableSpec(128, 1, 1, true));
        l.sinks.put(2, new EnergyStorage(2048, 32));
        EnergyNet.emit(l, 0, new int[]{5}, 128, 1, 0);
        assertTrue(l.exploded);
    }

    @Test
    void overloadedCableHeatsUpAndBurns() {
        Line l = new Line(2, TIN);
        EnergyStorage m = new EnergyStorage(1_000_000, 32);
        m.maxAmperesIn = 10;
        l.sinks.put(3, m);
        boolean burnt = false;
        for (int t = 0; t < 10 && !burnt; t++) {
            l.tick = t;
            EnergyNet.emit(l, 0, new int[]{5}, 32, 2, t); // 2 A по проводу на 1 A
            burnt = l.states.get(1).burnt || l.states.get(2).burnt;
        }
        assertTrue(burnt, "a 1 A cable carrying 2 A burns within a few ticks");
    }

    @Test
    void overheatCoolsDownOverTime() {
        CableState s = new CableState();
        s.touch(0);
        s.overheat = 300;
        s.touch(100);
        assertEquals(200, s.overheat);
    }

    @Test
    void machineAcceptsLimitedAmperesPerTick() {
        EnergyStorage m = new EnergyStorage(2048, 32);
        m.maxAmperesIn = 1;
        assertEquals(1, m.inject(0, 32, 4).amperes());
        assertEquals(0, m.inject(0, 32, 4).amperes(), "same tick");
        assertEquals(1, m.inject(1, 32, 4).amperes(), "next tick");
    }

    @Test
    void tiersAndEmissionCost() {
        assertEquals(0, EnergyNet.tier(8));
        assertEquals(1, EnergyNet.tier(32));
        assertEquals(2, EnergyNet.tier(33));
        assertEquals(34, EnergyNet.emissionCost(32), "GT5: 32 V costs 34 EU per ampere");
        assertEquals(4f, EnergyNet.shockDamage(32, 1));
    }

    @Test
    void generatorBurnsSteam() {
        GeneratorLogic g = new GeneratorLogic(32, 512);
        EnergyStorage e = new EnergyStorage(g.capacity(), 0);
        long used = g.burn(e, 16000, GeneratorLogic.STEAM_TURBINE_EU_PER_OPERATION, GeneratorLogic.steamTurbineLitersPerOperation(1));
        assertEquals(1792, g.capacity());
        assertEquals(597 * 7, used, "fills 1792 EU with 3 EU per 7 L");
        assertEquals(1791, e.stored);
        assertEquals(37, g.usableAmperes(e, 100), "(1791-512)/34");
    }
}
