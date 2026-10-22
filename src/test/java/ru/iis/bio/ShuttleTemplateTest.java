package ru.iis.bio;

import org.junit.jupiter.api.Test;
import ru.iis.bio.world.ShuttleTemplate;
import ru.iis.bio.world.ShuttleTemplate.Cell;
import ru.iis.bio.world.ShuttleTemplate.Kind;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShuttleTemplateTest {
    private static Map<String, Cell> byPos(List<Cell> cells) {
        Map<String, Cell> m = new HashMap<>();
        for (Cell c : cells) assertNull(m.put(c.x() + "," + c.y() + "," + c.z(), c), "duplicate cell " + c);
        return m;
    }

    @Test
    void fitsTheBoundingBox() {
        for (Cell c : ShuttleTemplate.cells()) {
            assertTrue(c.x() >= 0 && c.x() < ShuttleTemplate.LX, "x " + c);
            assertTrue(c.z() >= 0 && c.z() < ShuttleTemplate.LZ, "z " + c);
            assertTrue(c.y() >= 0 && c.y() < ShuttleTemplate.LY, "y " + c);
        }
    }

    @Test
    void hasExactlyOneCoreAndMawNextToItInTheRoof() {
        Map<String, Cell> m = byPos(ShuttleTemplate.cells());
        long cores = m.values().stream().filter(c -> c.kind() == Kind.CORE).count();
        long maws = m.values().stream().filter(c -> c.kind() == Kind.MAW).count();
        assertEquals(1, cores);
        assertEquals(1, maws);
        Cell core = m.get(ShuttleTemplate.CORE_X + "," + ShuttleTemplate.CORE_Y + "," + ShuttleTemplate.CORE_Z);
        assertEquals(Kind.CORE, core.kind());
        assertEquals(Kind.MAW, m.get(ShuttleTemplate.MAW_X + "," + ShuttleTemplate.CORE_Y + "," + ShuttleTemplate.CORE_Z).kind());
    }

    @Test
    void infectionSitsOnTheOutsideOfTheHull() {
        Map<String, Cell> m = byPos(ShuttleTemplate.cells());
        // ядро и пасть — часть крыши: над ними ничего нет (открыто небо), под ними — полость кабины, по бокам — плиты обшивки
        assertNull(m.get(ShuttleTemplate.CORE_X + "," + (ShuttleTemplate.CORE_Y + 1) + "," + ShuttleTemplate.CORE_Z));
        assertNull(m.get(ShuttleTemplate.MAW_X + "," + (ShuttleTemplate.CORE_Y + 1) + "," + ShuttleTemplate.CORE_Z));
        assertEquals(Kind.AIR, m.get(ShuttleTemplate.CORE_X + "," + (ShuttleTemplate.CORE_Y - 1) + "," + ShuttleTemplate.CORE_Z).kind());
        assertEquals(Kind.HULL, m.get(ShuttleTemplate.CORE_X + "," + ShuttleTemplate.CORE_Y + "," + (ShuttleTemplate.CORE_Z + 1)).kind());
        assertEquals(Kind.HULL, m.get(ShuttleTemplate.CORE_X + "," + ShuttleTemplate.CORE_Y + "," + (ShuttleTemplate.CORE_Z - 1)).kind());
        assertEquals(Kind.HULL, m.get((ShuttleTemplate.CORE_X - 1) + "," + ShuttleTemplate.CORE_Y + "," + ShuttleTemplate.CORE_Z).kind());
    }

    @Test
    void roofAroundTheOrgansIsIntactHull() {
        Map<String, Cell> m = byPos(ShuttleTemplate.cells());
        for (int z = 2; z <= 4; z++)
            for (int x = 4; x <= 7; x++) {
                Kind k = m.get(x + "," + ShuttleTemplate.CORE_Y + "," + z).kind();
                assertTrue(k == Kind.HULL || k == Kind.CORE || k == Kind.MAW, "roof cell " + x + "," + z + " is " + k);
            }
    }

    @Test
    void cabinIsHollowAndHasAllPartKinds() {
        Set<Kind> kinds = new HashSet<>();
        for (Cell c : ShuttleTemplate.cells()) kinds.add(c.kind());
        assertEquals(Set.of(Kind.HULL, Kind.WINDOW, Kind.ENGINE, Kind.AIR, Kind.CORE, Kind.MAW), kinds);
    }

    @Test
    void noseIsBuriedDeeperThanTheTail() {
        assertTrue(ShuttleTemplate.sink(0) > ShuttleTemplate.sink(9));
        assertEquals(0, ShuttleTemplate.sink(9));
    }

    @Test
    void rotationsAreBijectionsOntoTheirExtent() {
        for (int rot = 0; rot < 4; rot++) {
            for (boolean mirror : new boolean[]{false, true}) {
                int[] ext = ShuttleTemplate.extent(rot);
                Set<String> seen = new HashSet<>();
                for (int x = 0; x < ShuttleTemplate.LX; x++)
                    for (int z = 0; z < ShuttleTemplate.LZ; z++) {
                        int[] t = ShuttleTemplate.transform(x, z, rot, mirror);
                        assertTrue(t[0] >= 0 && t[0] < ext[0] && t[1] >= 0 && t[1] < ext[1], "rot " + rot + " " + t[0] + "," + t[1]);
                        assertTrue(seen.add(t[0] + "," + t[1]));
                    }
                assertEquals(ShuttleTemplate.LX * ShuttleTemplate.LZ, seen.size());
            }
        }
    }
}
