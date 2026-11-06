package ru.iis.energy.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Передача EU в FE: GT_MetaPipeEntity_Cable, блоки RF Compat и IC2 Compat (D125). */
class FeTransferTest {
    /** Приёмник с запасом места. */
    static final class Tank implements FeTransfer.Sink {
        long room, got;

        Tank(long room) {
            this.room = room;
        }

        @Override
        public long insert(long amount, boolean simulate) {
            long n = Math.min(amount, room);
            if (!simulate) {
                room -= n;
                got += n;
            }
            return n;
        }
    }

    @Test
    void rfGives360Per100AndOneAmpere() {
        Tank t = new Tank(100000);
        assertEquals(1, FeTransfer.rf(t, 32, new CableState()));
        assertEquals(115, t.got); // 32 * 360 / 100
    }

    @Test
    void rfKeepsTheRestOnTheCable() {
        Tank t = new Tank(100);
        CableState c = new CableState();
        assertEquals(1, FeTransfer.rf(t, 32, c));
        assertEquals(100, t.got);
        assertEquals(15, c.feRest);
        t.room = 10;
        assertEquals(0, FeTransfer.rf(t, 32, c));
        assertEquals(110, t.got);
        assertEquals(5, c.feRest);
    }

    @Test
    void fullSinkTakesNothing() {
        CableState c = new CableState();
        assertEquals(0, FeTransfer.rf(new Tank(0), 32, c));
        assertEquals(0, c.feRest);
        assertEquals(0, FeTransfer.ae2(new Tank(0), 32, 4));
    }

    @Test
    void ae2TakesAmperesWhileItAsks() {
        Tank t = new Tank(1000000);
        assertEquals(4, FeTransfer.ae2(t, 32, 4));
        assertEquals(4 * 128, t.got); // 1 EU = 2 AE = 4 FE
    }

    @Test
    void ae2StopsWhenFull() {
        Tank t = new Tank(200);
        assertEquals(2, FeTransfer.ae2(t, 32, 4));
        assertEquals(200, t.got);
    }
}
