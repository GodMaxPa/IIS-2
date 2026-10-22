package ru.iis.bio.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Форма личного аварийного челнока (чистая геометрия, без Minecraft; D68). Беглец с заражённого материнского корабля: веретено
 * 10×7×5 с носовым остеклением, крыльями (правое обломано), хвостовым плавником, тремя двигателями. Заражение сидит на обшивке снаружи:
 * ядро и пасть вделаны в крышу заподлицо с плитами (D69), ткань расползается по корпусу. Нос врезался в землю глубже кормы (см. {@link #sink}). Локальные оси: x — от носа (0) к корме (9),
 * z — поперёк (центр 3), y — от днища среза (0). Один шаблон поворачивается и отражается при установке.
 */
public final class ShuttleTemplate {
    private ShuttleTemplate() {}

    public enum Kind { HULL, WINDOW, ENGINE, AIR, CORE, MAW }

    public record Cell(int x, int y, int z, Kind kind) {}

    public static final int LX = 10, LZ = 7, LY = 5;

    /** Ядро вделано в крышу (вместо плиты обшивки), пасть — рядом, тоже в крыше. */
    public static final int CORE_X = 5, CORE_Y = 3, CORE_Z = 3, MAW_X = 6;

    // z-диапазон корпуса в каждой колонке x и высота крыши (включительно) — веретено
    private static final int[] ZMIN = {3, 2, 1, 1, 1, 1, 1, 1, 2, 2};
    private static final int[] ZMAX = {3, 4, 5, 5, 5, 5, 5, 5, 4, 4};
    private static final int[] ROOF = {1, 2, 3, 3, 3, 3, 3, 3, 2, 2};

    /** На сколько блоков днище этой колонки ниже днища кормы: нос зарылся в землю. */
    public static int sink(int x) {
        return x <= 1 ? 2 : x <= 3 ? 1 : 0;
    }

    private static boolean inFoot(int x, int z) {
        return x >= 0 && x < LX && z >= ZMIN[x] && z <= ZMAX[x];
    }

    /** Все клетки челнока, которые нужно поставить или расчистить (AIR — расчищаем: салон, пробоина, дыры в обшивке). */
    public static List<Cell> cells() {
        List<Cell> out = new ArrayList<>();
        for (int x = 0; x < LX; x++) {
            for (int z = ZMIN[x]; z <= ZMAX[x]; z++) {
                for (int y = 0; y <= ROOF[x]; y++) {
                    Kind k;
                    if (y == 0) {
                        k = Kind.HULL;
                    } else if (y == ROOF[x]) {
                        k = Kind.HULL;
                    } else {
                        boolean outline = !inFoot(x - 1, z) || !inFoot(x + 1, z) || !inFoot(x, z - 1) || !inFoot(x, z + 1);
                        k = outline ? Kind.HULL : Kind.AIR;
                    }
                    // фонарь кабины: нос и скат крыши
                    if (k == Kind.HULL && y == ROOF[x] && z >= 2 && z <= 4 && ((x == 1) || (x == 2 || x == 3))) k = Kind.WINDOW;
                    // рваные дыры в обшивке
                    if ((x == 3 && y == 2 && z == 1) || (x == 7 && (y == 1 || y == 2) && z == 5)) k = Kind.AIR;
                    // двигатели на хвостовой стенке
                    if (x == 9 && ((y == 1 && (z == 2 || z == 4)) || (y == 2 && z == 3))) k = Kind.ENGINE;
                    if (x == CORE_X && y == CORE_Y && z == CORE_Z) k = Kind.CORE;
                    if (x == MAW_X && y == CORE_Y && z == CORE_Z) k = Kind.MAW;
                    out.add(new Cell(x, y, z, k));
                }
            }
        }
        // крылья (правое — z=6 — обломано до пенька)
        for (int x = 4; x <= 6; x++)
            for (int y = 0; y <= 1; y++) {
                out.add(new Cell(x, y, 0, Kind.HULL));
                if (x == 4 || (y == 0 && x == 5)) out.add(new Cell(x, y, 6, Kind.HULL));
            }
        // хвостовой плавник
        out.add(new Cell(8, 3, 3, Kind.HULL));
        out.add(new Cell(8, 4, 3, Kind.HULL));
        out.add(new Cell(9, 3, 3, Kind.HULL));
        return out;
    }

    /** Преобразование локальных (x, z) в мировые смещения: поворот на 90°·rot и зеркало по z. */
    public static int[] transform(int x, int z, int rot, boolean mirror) {
        int lz = mirror ? LZ - 1 - z : z;
        return switch (rot & 3) {
            case 0 -> new int[]{x, lz};
            case 1 -> new int[]{LZ - 1 - lz, x};
            case 2 -> new int[]{LX - 1 - x, LZ - 1 - lz};
            default -> new int[]{lz, LX - 1 - x};
        };
    }

    /** Размеры охватывающего прямоугольника после поворота: {по X, по Z}. */
    public static int[] extent(int rot) {
        return (rot & 1) == 0 ? new int[]{LX, LZ} : new int[]{LZ, LX};
    }
}
