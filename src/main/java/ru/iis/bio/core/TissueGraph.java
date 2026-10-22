package ru.iis.bio.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Связность живой ткани (чистая геометрия, без Minecraft). Клетка ткани — квадрат на грани блока: ячейка (x, y, z) + грань
 * (0 вниз, 1 вверх, 2 север, 3 юг, 4 запад, 5 восток — как {@code Direction}). Два квадрата соединены напрямую, только если у них
 * общее ребро: в одной ячейке на разных (не противоположных) гранях; в соседних ячейках на одной грани (одна плоскость);
 * или вокруг выпуклого ребра блока (ячейка c на грани f и ячейка c + f − g на грани g, f ⟂ g).
 * Ткань не имеет права разрываться: всё, что не достаёт до корня, отмирает; отступать можно только с самого дальнего конца.
 */
public final class TissueGraph {
    private TissueGraph() {}

    public static final int[] DX = {0, 0, 0, 0, -1, 1};
    public static final int[] DY = {-1, 1, 0, 0, 0, 0};
    public static final int[] DZ = {0, 0, -1, 1, 0, 0};

    public record Square(int x, int y, int z, int face) {}

    public static int opposite(int face) {
        return face ^ 1;
    }

    private static boolean perpendicular(int a, int b) {
        return a != b && a != opposite(b);
    }

    /** Все квадраты, напрямую соединённые с данным (12 возможных мест, независимо от того, есть ли там ткань). */
    public static List<Square> neighbours(Square s) {
        List<Square> out = new ArrayList<>(12);
        int f = s.face;
        for (int g = 0; g < 6; g++) {
            if (!perpendicular(f, g)) continue;
            out.add(new Square(s.x, s.y, s.z, g));                                              // та же ячейка, другая грань
            out.add(new Square(s.x + DX[g], s.y + DY[g], s.z + DZ[g], f));                      // соседняя ячейка, та же плоскость
            out.add(new Square(s.x + DX[f] - DX[g], s.y + DY[f] - DY[g], s.z + DZ[f] - DZ[g], g)); // вокруг ребра блока
        }
        return out;
    }

    /** Расстояние (в шагах) от корня до каждого достижимого квадрата; недостижимые в результате отсутствуют. */
    public static Map<Square, Integer> distances(Set<Square> squares, Predicate<Square> isRoot) {
        Map<Square, Integer> dist = new HashMap<>();
        ArrayDeque<Square> queue = new ArrayDeque<>();
        for (Square s : squares) {
            if (isRoot.test(s)) {
                dist.put(s, 0);
                queue.add(s);
            }
        }
        while (!queue.isEmpty()) {
            Square cur = queue.poll();
            int d = dist.get(cur);
            for (Square n : neighbours(cur)) {
                if (squares.contains(n) && !dist.containsKey(n)) {
                    dist.put(n, d + 1);
                    queue.add(n);
                }
            }
        }
        return dist;
    }
}
