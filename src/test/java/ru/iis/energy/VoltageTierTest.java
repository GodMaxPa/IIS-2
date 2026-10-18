package ru.iis.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoltageTierTest {

    @Test
    void valuesMatchGT5() {
        long[] gt5 = {8, 32, 128, 512, 2048, 8192, 32768, 131072, 524288, Integer.MAX_VALUE};
        VoltageTier[] tiers = VoltageTier.values();
        assertEquals(gt5.length, tiers.length);
        for (int i = 0; i < gt5.length; i++) assertEquals(gt5[i], tiers[i].voltage(), tiers[i].name());
    }

    @Test
    void eachTierIsFourTimesPrevious() {
        VoltageTier[] t = VoltageTier.values();
        for (int i = 1; i < t.length - 1; i++) assertEquals(t[i - 1].voltage() * 4, t[i].voltage());
    }

    @Test
    void forVoltagePicksSmallestFittingTier() {
        assertEquals(VoltageTier.ULV, VoltageTier.forVoltage(0));
        assertEquals(VoltageTier.ULV, VoltageTier.forVoltage(8));
        assertEquals(VoltageTier.LV, VoltageTier.forVoltage(9));
        assertEquals(VoltageTier.LV, VoltageTier.forVoltage(32));
        assertEquals(VoltageTier.MV, VoltageTier.forVoltage(33));
        assertEquals(VoltageTier.MAX, VoltageTier.forVoltage(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> VoltageTier.forVoltage(-1));
    }

    @Test
    void byTierRoundTrips() {
        for (VoltageTier t : VoltageTier.values()) assertEquals(t, VoltageTier.byTier(t.tier()));
        assertThrows(IllegalArgumentException.class, () -> VoltageTier.byTier(99));
    }
}
