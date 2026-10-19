package ru.iis.pollution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Числа GT_Pollution: распад, растекание, глушитель, попытки погубить растения. */
class PollutionLogicTest {
    @Test
    void decayIsOnePercentMinus2000() {
        assertEquals(97000, PollutionLogic.decay(100000));
        assertEquals(0, PollutionLogic.decay(1500));
        assertEquals(0, PollutionLogic.decay(0));
    }

    @Test
    void spreadsOnlyAbove50000AndTowardsCleanerChunks() {
        int[] n = {0, 0, 0, 0};
        assertEquals(40000, PollutionLogic.spread(40000, n));
        assertArrayEquals(new int[]{0, 0, 0, 0}, n);

        n = new int[]{0, 0, 90000, 100000};
        int left = PollutionLogic.spread(100000, n);
        // сосед 0: уходит 10000; сосед 1: (90000-0)/10 = 9000; сосед 90000: 90000*6 < 81000*5? нет; сосед 100000: нет
        assertEquals(10000, n[0]);
        assertEquals(9000, n[1]);
        assertEquals(90000, n[2]);
        assertEquals(81000, left);
        assertEquals(100000 + 90000 + 100000, left + n[0] + n[1] + n[2] + n[3] - 0, "pollution is conserved");
    }

    @Test
    void steadySourceReachesSmogLikeGtComment() {
        // GT: до смога (500k) в одиночном чанке нужен источник ≈ 466 в секунду (с учётом растекания в 4 стороны);
        // без растекания одного чанка: p = 0.99p − 2000 + S·60 → равновесие p = (60S − 2000) / 0.01
        int s = 20 * 60; // бронзовый котёл за цикл
        int p = 0;
        for (int i = 0; i < 2000; i++) p = PollutionLogic.decay(p + s);
        assertTrue(p < PollutionLogic.SPREAD_THRESHOLD, "один котёл не доводит чанк до растекания: " + p);
    }

    @Test
    void mufflerTiers() {
        assertEquals(10000, PollutionLogic.mufflerOutput(10000, 1));
        assertEquals(7000, PollutionLogic.mufflerOutput(10000, 2));
    }

    @Test
    void vegetationAttempts() {
        assertEquals(0, PollutionLogic.vegetationAttempts(500000));
        assertEquals(20, PollutionLogic.vegetationAttempts(1000000));
    }
}
