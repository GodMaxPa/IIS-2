package ru.iis.machine.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Форма примитивной кирпичной доменной печи GT5-II (GT_MetaTileEntity_PrimitiveBlastFurnace#checkMachine).
 *
 * Куб 3×4×3 за контроллером: нижний слой и стенки из огнеупорных кирпичей (32 блока),
 * центральный столб над нижним слоем (3 блока) пустой — воздух или лава. Контроллер стоит в центре
 * передней стенки на высоте второго снизу слоя.
 *
 * Координаты: x вправо от контроллера, y вверх, z вглубь (от лицевой стороны внутрь печи).
 */
public final class BbfStructure {
    private BbfStructure() {}

    public enum Cell { CASING, HOLLOW }

    public record Pos(int right, int up, int back) {}

    public interface World {
        boolean isCasing(Pos p);

        /** Воздух или лава. */
        boolean isAirOrLava(Pos p);
    }

    /** Все клетки формы (кроме самого контроллера) с ожидаемым содержимым. */
    public static List<Cell> cells(List<Pos> outPositions) {
        List<Cell> cells = new ArrayList<>();
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 3; j++) {
                for (int k = -1; k < 2; k++) {
                    int back = 1 + k;
                    if (i == 0 && j == 0 && back == 0) continue; // контроллер
                    outPositions.add(new Pos(i, j, back));
                    cells.add((i != 0 || j == -1 || k != 0) ? Cell.CASING : Cell.HOLLOW);
                }
            }
        }
        return cells;
    }

    public static boolean check(World world) {
        List<Pos> pos = new ArrayList<>();
        List<Cell> cells = cells(pos);
        for (int n = 0; n < cells.size(); n++) {
            Pos p = pos.get(n);
            boolean ok = cells.get(n) == Cell.CASING ? world.isCasing(p) : world.isAirOrLava(p);
            if (!ok) return false;
        }
        return true;
    }

    /** Клетки центрального столба, куда работающая печь ставит лаву (нижние две, как в GT5). */
    public static final Pos LAVA_LOW = new Pos(0, 0, 1), LAVA_HIGH = new Pos(0, 1, 1);
}
