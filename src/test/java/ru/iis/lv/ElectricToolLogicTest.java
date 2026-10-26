package ru.iis.lv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Электроинструменты GT5: заряд вместо прочности, износ 1/25, цена блока, заряд при сборке (D78). */
class ElectricToolLogicTest {
    @Test
    void actionCostsEuAndUsuallyKeepsTheHead() {
        ElectricToolLogic.Result r = ElectricToolLogic.use(100000, 1000, 7);
        assertEquals(99000, r.charge());
        assertEquals(0, r.wear());
        assertTrue(r.worked());
    }

    @Test
    void oneRollInTwentyFiveWearsTheHead() {
        ElectricToolLogic.Result r = ElectricToolLogic.use(100000, 1000, 0);
        assertEquals(99000, r.charge());
        assertEquals(20, r.wear(), "1000 GT = 20 points");
        assertEquals(1, ElectricToolLogic.use(1000, 50, 0).wear());
        assertEquals(1, ElectricToolLogic.use(1000, 10, 0).wear(), "at least one point");
    }

    @Test
    void notEnoughChargeTakesTheRestAndFails() {
        ElectricToolLogic.Result r = ElectricToolLogic.use(30, 50, 0);
        assertEquals(0, r.charge());
        assertEquals(0, r.wear());
        assertFalse(r.worked());
    }

    @Test
    void blockCostFollowsHardness() {
        assertEquals(75, ElectricToolLogic.blockCost(1.5f, 50), "stone with the LV drill");
        assertEquals(100, ElectricToolLogic.blockCost(2.0f, 50), "log with the LV chainsaw");
        assertEquals(1, ElectricToolLogic.blockCost(0.0f, 50), "instant blocks still cost 1 EU");
        assertEquals(25, ElectricToolLogic.blockCost(0.5f, 50));
    }

    @Test
    void craftedToolTakesBatteryCharge() {
        assertEquals(42000, ElectricToolLogic.craftedCharge(42000, 100000));
        assertEquals(50000, ElectricToolLogic.craftedCharge(80000, 50000));
        assertEquals(0, ElectricToolLogic.craftedCharge(0, 75000));
    }
}
