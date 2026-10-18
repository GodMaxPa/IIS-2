package ru.iis.machine.logic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BbfStructureTest {
    @Test
    void shapeIs32FirebricksAnd3Hollow() {
        List<BbfStructure.Pos> pos = new ArrayList<>();
        List<BbfStructure.Cell> cells = BbfStructure.cells(pos);
        assertEquals(35, cells.size());
        assertEquals(32, cells.stream().filter(c -> c == BbfStructure.Cell.CASING).count());
        assertEquals(Set.of(new BbfStructure.Pos(0, 0, 1), new BbfStructure.Pos(0, 1, 1), new BbfStructure.Pos(0, 2, 1)),
                new HashSet<>(pos.subList(0, 0)) {{
                    for (int i = 0; i < cells.size(); i++) if (cells.get(i) == BbfStructure.Cell.HOLLOW) add(pos.get(i));
                }});
        assertFalse(pos.contains(new BbfStructure.Pos(0, 0, 0)), "controller cell is not part of the check");
    }

    @Test
    void check() {
        Set<BbfStructure.Pos> casings = new HashSet<>();
        List<BbfStructure.Pos> pos = new ArrayList<>();
        List<BbfStructure.Cell> cells = BbfStructure.cells(pos);
        for (int i = 0; i < cells.size(); i++) if (cells.get(i) == BbfStructure.Cell.CASING) casings.add(pos.get(i));
        BbfStructure.World ok = new BbfStructure.World() {
            @Override public boolean isCasing(BbfStructure.Pos p) { return casings.contains(p); }
            @Override public boolean isAirOrLava(BbfStructure.Pos p) { return !casings.contains(p); }
        };
        assertTrue(BbfStructure.check(ok));
        casings.remove(new BbfStructure.Pos(1, 2, 2));
        assertFalse(BbfStructure.check(ok));
    }
}
