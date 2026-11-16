package ru.iis.implant;

import org.junit.jupiter.api.Test;
import ru.iis.implant.ImplantLogic.Kind;
import ru.iis.implant.ImplantLogic.Slot;
import ru.iis.implant.ImplantLogic.Tier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Правила киберимплантов (D157): области, ёмкость тела, расход, включение и выключение. */
class ImplantLogicTest {
    private static Kind[] body(Kind... kinds) {
        Kind[] b = new Kind[Slot.values().length];
        for (Kind k : kinds) b[k.slot().ordinal()] = k;
        return b;
    }

    @Test
    void fourteenImplantsWithUniqueIds() {
        assertEquals(14, ImplantLogic.KINDS.size());
        assertEquals(14, ImplantLogic.KINDS.stream().map(Kind::id).distinct().count());
        for (Kind k : ImplantLogic.KINDS) assertEquals(k, ImplantLogic.byId(k.id()));
        assertNull(ImplantLogic.byId(""));
    }

    @Test
    void noCellNoWork() {
        Kind[] b = body(new Kind(Slot.HEART, Tier.HV));
        assertEquals(0, ImplantLogic.capacity(b));
        ImplantLogic.Step s = ImplantLogic.step(b, 500_000, 20);
        assertFalse(s.active());
        assertEquals(0, s.charge(), "charge cannot stay in a body with no cell");
    }

    @Test
    void drainAndSwitchOff() {
        Kind[] b = body(new Kind(Slot.CELL, Tier.HV), new Kind(Slot.EYES, Tier.HV), new Kind(Slot.HEART, Tier.HV), new Kind(Slot.LEGS, Tier.EV));
        assertEquals(1 + 2 + 4, ImplantLogic.drain(b));
        ImplantLogic.Step s = ImplantLogic.step(b, 1000, 20);
        assertTrue(s.active());
        assertEquals(1000 - 7 * 20, s.charge());
        ImplantLogic.Step low = ImplantLogic.step(b, 100, 20);
        assertFalse(low.active(), "not enough for a full second: implants switch off");
        assertEquals(100, low.charge(), "an inactive body does not burn its last charge");
        assertEquals(Tier.HV.capacity, ImplantLogic.step(b, Long.MAX_VALUE, 0).charge(), "charge is capped by the cell");
    }

    @Test
    void fullSetsLastAsTheQuestsSay() {
        Kind[] hv = new Kind[Slot.values().length], ev = new Kind[Slot.values().length];
        for (Slot s : Slot.values()) {
            hv[s.ordinal()] = new Kind(s, Tier.HV);
            ev[s.ordinal()] = new Kind(s, Tier.EV);
        }
        assertEquals(11, ImplantLogic.drain(hv));
        assertEquals(22, ImplantLogic.drain(ev));
        assertTrue(Tier.EV.capacity / 22 / 20 / 60 >= 150, "an EV cell lasts two and a half hours");
    }

    @Test
    void bodyRoundTrips() {
        Kind[] b = body(new Kind(Slot.CELL, Tier.EV), new Kind(Slot.SKIN, Tier.HV));
        List<String> ids = ImplantLogic.ids(b);
        assertEquals(7, ids.size());
        assertArrayEquals(b, ImplantLogic.parse(ids));
        assertArrayEquals(new Kind[7], ImplantLogic.parse(List.of()));
    }
}
