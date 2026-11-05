package ru.iis.reactor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Правила реактора (D117): числа стержней GT_Loader_Item_Block_And_Fluid:119–152 и логика GT_RadioactiveCellIC_Item. */
class ReactorCoreTest {
    private static ReactorCore core(int columns) {
        ReactorCore c = new ReactorCore();
        c.columns = columns;
        c.active = true;
        return c;
    }

    private static void put(ReactorCore c, int x, int y, String id) {
        c.cells[x + y * ReactorCore.WIDTH] = new ReactorCore.Cell(id, ReactorCore.PARTS.get(id), 0);
    }

    private static ReactorCore.Cell at(ReactorCore c, int x, int y) {
        return c.cells[x + y * ReactorCore.WIDTH];
    }

    @Test
    void lonelyUraniumRod() {
        ReactorCore c = core(3);
        put(c, 1, 1, "rod_uranium_1");
        // один импульс: выход 2 × 1 = 2, то есть 10 EU/t; тепло T(1) × 4 = 4 в корпус за секунду
        assertEquals(10F, c.second(), 1e-3);
        assertEquals(4, c.heat);
        assertEquals(1, at(c, 1, 1).damage);
    }

    @Test
    void quadRodPulsesItself() {
        ReactorCore c = core(3);
        put(c, 1, 1, "rod_uranium_4");
        // 4 стержня по 3 импульса: выход 4 × 3 × 2 = 24, 120 EU/t; тепло 4 × T(3) × 4 = 96
        assertEquals(120F, c.second(), 1e-3);
        assertEquals(96, c.heat);
    }

    @Test
    void neighbouringRodsPulseEachOther() {
        ReactorCore c = core(3);
        put(c, 0, 0, "rod_uranium_1");
        put(c, 1, 0, "rod_uranium_1");
        // у каждого 2 импульса: выход 4 × 2 = 8, 40 EU/t; тепло 2 × T(2) × 4 = 24
        assertEquals(40F, c.second(), 1e-3);
        assertEquals(24, c.heat);
    }

    @Test
    void reflectorReturnsThePulseAndWears() {
        ReactorCore c = core(3);
        put(c, 0, 0, "rod_uranium_1");
        put(c, 1, 0, "reactor_reflector");
        assertEquals(20F, c.second(), 1e-3);
        assertEquals(2, at(c, 1, 0).damage, "по разу в каждом проходе");
        ReactorCore d = core(3);
        put(d, 0, 0, "rod_uranium_1");
        put(d, 1, 0, "neutron_reflector");
        assertEquals(20F, d.second(), 1e-3);
        assertEquals(0, at(d, 1, 0).damage, "иридиевый не изнашивается");
    }

    @Test
    void ventTakesRodHeatAndCoolsItself() {
        ReactorCore c = core(3);
        put(c, 0, 0, "rod_uranium_1");
        put(c, 1, 0, "reactor_vent");
        c.second();
        assertEquals(0, c.heat, "всё тепло ушло в отвод");
        // отвод обрабатывается позже стержня в том же проходе: 4 − 6 → 0
        assertEquals(0, at(c, 1, 0).damage);
    }

    @Test
    void overheatedPartBreaks() {
        ReactorCore c = core(3);
        put(c, 0, 0, "rod_uranium_4");
        put(c, 1, 0, "coolant_10k");
        at(c, 1, 0).damage = 10000;
        c.second();
        assertTrue(at(c, 1, 0).removed, "стержень охлаждения сгорел");
    }

    @Test
    void coreVentDrainsTheHull() {
        ReactorCore c = core(3);
        put(c, 0, 0, "reactor_vent_gold");
        c.heat = 1000;
        c.second();
        assertEquals(964, c.heat, "36 из корпуса");
        assertEquals(16, at(c, 0, 0).damage, "36 − 20 на себя");
    }

    @Test
    void platingRaisesTheLimitAndWeakensTheBlast() {
        ReactorCore c = core(3);
        put(c, 0, 0, "reactor_plating");
        put(c, 1, 0, "rod_uranium_4");
        float without = 10 + 8;
        c.second();
        assertEquals(11000, c.maxHeat);
        assertEquals(without * 0.95F, c.explosionPower(), 1e-3);
    }

    @Test
    void explosionHasACeiling() {
        ReactorCore c = core(9);
        for (int y = 0; y < ReactorCore.HEIGHT; y++) for (int x = 0; x < 9; x++) put(c, x, y, "rod_uranium_4");
        assertEquals(ReactorCore.EXPLOSION_LIMIT, c.explosionPower(), 1e-3);
    }

    @Test
    void spentRodIsReplaced() {
        ReactorCore c = core(3);
        put(c, 0, 0, "rod_uranium_1");
        at(c, 0, 0).damage = 19999;
        c.second();
        assertEquals("rod_depleted_uranium_1", at(c, 0, 0).replacedBy);
    }

    @Test
    void offReactorDoesNothingButVentsCool() {
        ReactorCore c = core(3);
        c.active = false;
        put(c, 0, 0, "rod_uranium_1");
        put(c, 1, 0, "reactor_vent");
        at(c, 1, 0).damage = 100;
        assertEquals(0F, c.second(), 1e-3);
        assertEquals(0, at(c, 0, 0).damage);
        assertEquals(94, at(c, 1, 0).damage);
    }

    @Test
    void moxOutputGrowsWithHeat() {
        ReactorCore c = core(3);
        put(c, 0, 0, "rod_mox_1");
        c.heat = 5000;
        // k = 1.5 × 5000 / 10000 + 1 = 1.75; тепло стержня прибавится раньше прохода энергии: 5004
        assertEquals(1.75F * 2 * 5, c.second(), 0.01);
    }
}
