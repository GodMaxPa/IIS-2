package ru.iis.machine;

import org.junit.jupiter.api.Test;
import ru.iis.energy.VoltageTier;

import static org.junit.jupiter.api.Assertions.*;

/** Ожидаемые значения посчитаны вручную по формуле GT5-II calculateOverclockedNess. */
class OverclockTest {

    @Test
    void lvMachineRunsLvRecipeAsIs() {
        // 30 EU/t > 8 (ULV) -> разгона нет
        assertEquals(new Overclock.Result(30, 400), Overclock.basicMachine(30, 400, VoltageTier.LV, 1));
    }

    @Test
    void mvMachineOverclocksLvRecipeOnce() {
        // 30 <= 32 -> 120 EU/t, 200 тиков; 120 > 32 -> стоп
        assertEquals(new Overclock.Result(120, 200), Overclock.basicMachine(30, 400, VoltageTier.MV, 1));
    }

    @Test
    void hvMachineOverclocksLvRecipeTwice() {
        // 30 -> 120 -> 480 (<=512 уровня HV), 400 -> 200 -> 100
        assertEquals(new Overclock.Result(480, 100), Overclock.basicMachine(30, 400, VoltageTier.HV, 1));
    }

    @Test
    void lowPowerRecipesScaleByTier() {
        // <=16 EU/t: LV x1, MV x4 и /2, HV x16 и /4
        assertEquals(new Overclock.Result(2, 400), Overclock.basicMachine(2, 400, VoltageTier.LV, 1));
        assertEquals(new Overclock.Result(8, 200), Overclock.basicMachine(2, 400, VoltageTier.MV, 1));
        assertEquals(new Overclock.Result(32, 100), Overclock.basicMachine(2, 400, VoltageTier.HV, 1));
    }

    @Test
    void amperageRaisesOverclockLimit() {
        // MV, 2A: предел 32*2=64 -> 30 -> 120 (стоп, 120 > 64)
        assertEquals(new Overclock.Result(120, 200), Overclock.basicMachine(30, 400, VoltageTier.MV, 2));
        // MV, 4A: предел 128 -> 30 -> 120 -> 480
        assertEquals(new Overclock.Result(480, 100), Overclock.basicMachine(30, 400, VoltageTier.MV, 4));
    }

    @Test
    void durationNeverDropsBelowOneTick() {
        assertEquals(1, Overclock.basicMachine(30, 1, VoltageTier.IV, 1).durationTicks());
        assertEquals(1, Overclock.basicMachine(1, 1, VoltageTier.IV, 1).durationTicks());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Overclock.basicMachine(0, 10, VoltageTier.LV, 1));
        assertThrows(IllegalArgumentException.class, () -> Overclock.basicMachine(10, 0, VoltageTier.LV, 1));
        assertThrows(IllegalArgumentException.class, () -> Overclock.basicMachine(10, 10, VoltageTier.ULV, 1));
        assertThrows(IllegalArgumentException.class, () -> Overclock.basicMachine(10, 10, VoltageTier.LV, 0));
    }
}
