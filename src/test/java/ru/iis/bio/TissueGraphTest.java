package ru.iis.bio;

import org.junit.jupiter.api.Test;
import ru.iis.bio.core.TissueGraph;
import ru.iis.bio.core.TissueGraph.Square;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TissueGraphTest {
    private static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    /** Корень — квадрат на грани ЗАПАД ячейки (1,0,0): прикреплён к ядру в (0,0,0). */
    private static boolean root(Square s) {
        return s.x() + TissueGraph.DX[s.face()] == 0 && s.y() + TissueGraph.DY[s.face()] == 0 && s.z() + TissueGraph.DZ[s.face()] == 0;
    }

    @Test
    void everySquareHasTwelveDirectNeighbours() {
        assertEquals(12, TissueGraph.neighbours(new Square(0, 0, 0, DOWN)).size());
        assertEquals(12, TissueGraph.neighbours(new Square(5, 5, 5, EAST)).size());
    }

    @Test
    void neighbourRelationIsSymmetric() {
        for (int f = 0; f < 6; f++) {
            Square s = new Square(2, 3, 4, f);
            for (Square n : TissueGraph.neighbours(s)) assertTrue(TissueGraph.neighbours(n).contains(s), s + " <-> " + n);
        }
    }

    @Test
    void floorChainIsConnectedFromTheCore() {
        Set<Square> all = new HashSet<>();
        all.add(new Square(1, 0, 0, WEST));    // на ядре
        all.add(new Square(1, 0, 0, DOWN));    // пол в той же ячейке
        all.add(new Square(2, 0, 0, DOWN));    // пол дальше
        all.add(new Square(3, 0, 0, DOWN));
        Map<Square, Integer> d = TissueGraph.distances(all, TissueGraphTest::root);
        assertEquals(4, d.size());
        assertEquals(3, d.get(new Square(3, 0, 0, DOWN)));
    }

    @Test
    void gapSplitsTheTissue() {
        Set<Square> all = new HashSet<>();
        all.add(new Square(1, 0, 0, WEST));
        all.add(new Square(1, 0, 0, DOWN));
        all.add(new Square(3, 0, 0, DOWN)); // (2,0,0) нет — разрыв
        all.add(new Square(4, 0, 0, DOWN));
        Map<Square, Integer> d = TissueGraph.distances(all, TissueGraphTest::root);
        assertEquals(2, d.size());
        assertFalse(d.containsKey(new Square(3, 0, 0, DOWN)));
    }

    @Test
    void diagonalWithoutSharedEdgeIsNotConnected() {
        Set<Square> all = new HashSet<>();
        all.add(new Square(1, 0, 0, WEST));
        all.add(new Square(1, 0, 0, DOWN));
        all.add(new Square(2, 0, 1, DOWN)); // по диагонали на том же полу — общей стороны нет
        assertEquals(2, TissueGraph.distances(all, TissueGraphTest::root).size());
    }

    @Test
    void wrapsAroundConcaveAndConvexEdges() {
        Set<Square> all = new HashSet<>();
        all.add(new Square(1, 0, 0, WEST));
        all.add(new Square(1, 0, 0, DOWN));
        all.add(new Square(1, 0, 0, NORTH));          // вогнутый угол: пол → стена в той же ячейке
        all.add(new Square(2, 0, 0, DOWN));
        all.add(new Square(3, -1, 0, WEST));          // выпуклое ребро: пол ячейки (2,0,0) → стена блока (2,-1,0) глазами ячейки (3,-1,0)
        Map<Square, Integer> d = TissueGraph.distances(all, TissueGraphTest::root);
        assertTrue(d.containsKey(new Square(1, 0, 0, NORTH)));
        assertTrue(d.containsKey(new Square(3, -1, 0, WEST)));
    }

    @Test
    void farthestSquareIsALeaf() {
        Set<Square> all = new HashSet<>();
        all.add(new Square(1, 0, 0, WEST));
        for (int x = 1; x <= 6; x++) all.add(new Square(x, 0, 0, DOWN));
        Map<Square, Integer> d = TissueGraph.distances(all, TissueGraphTest::root);
        Square far = d.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        all.remove(far);
        assertEquals(all.size(), TissueGraph.distances(all, TissueGraphTest::root).size(), "retreat from the far end keeps the tissue whole");
    }
}
