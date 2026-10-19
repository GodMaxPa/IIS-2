package ru.iis.world.vein;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OreGridLayoutTest {
    @Test
    void regionsTileTheWorldWithoutGaps() {
        // каждый чанк в -20..20 попадает ровно в один квадрат, и его центр — VeinGrid.centerChunk
        for (int c = -20; c <= 20; c++) {
            int center = VeinGrid.centerChunk(c);
            var r = new OreGridLayout.Region(center, center);
            assertTrue(c >= r.minChunkX() && c <= r.maxChunkX(), "chunk " + c + " outside region of " + center);
        }
    }

    @Test
    void aroundCountsAndCenters() {
        var list = OreGridLayout.around(0, 0, 2);
        assertEquals(25, list.size());
        assertTrue(list.contains(new OreGridLayout.Region(1, 1)));   // квадрат чанков 0..2
        assertTrue(list.contains(new OreGridLayout.Region(-2, -5))); // соседние квадраты
        list.forEach(r -> assertEquals(r.centerX(), VeinGrid.centerChunk(r.centerX())));
    }
}
