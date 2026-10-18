package ru.iis.world.vein;

import java.util.Random;

/**
 * Форма жилы GT5: плоский слой ~8 блоков высотой.
 * Перенесено из GT_Worldgen_GT_Ore_Layer#executeWorldgen, порядок обращений к Random сохранён.
 *
 * Слои относительно случайной высоты низа {@code y0}:
 *   y0-1 .. y0+1  вторичная руда
 *   y0+2 .. y0+3  промежуточная (одна случайная высота в колонке)
 *   y0+3 .. y0+5  основная
 *   y0-1 .. y0+5  вкрапления (одна случайная высота в колонке)
 * Вероятность блока падает к краям жилы, параметр {@code density} управляет этим.
 */
public final class VeinShape {
    private VeinShape() {}

    public enum Layer { PRIMARY, SECONDARY, BETWEEN, SPORADIC }

    @FunctionalInterface
    public interface Sink {
        void place(int x, int y, int z, Layer layer, String material);
    }

    /**
     * @param originX X первого блока центрального чанка (chunkX * 16)
     * @param originZ Z первого блока центрального чанка
     */
    public static void generate(VeinDefinition v, int originX, int originZ, Random random, Sink sink) {
        int y0 = v.minY() + random.nextInt(v.maxY() - v.minY() - 5);
        int cX = originX - random.nextInt(v.size());
        int eX = originX + 16 + random.nextInt(v.size());
        for (int x = cX; x <= eX; x++) {
            int cZ = originZ - random.nextInt(v.size());
            int eZ = originZ + 16 + random.nextInt(v.size());
            for (int z = cZ; z <= eZ; z++) {
                if (v.secondary() != null) {
                    for (int y = y0 - 1; y < y0 + 2; y++) {
                        if (hit(random, v.density(), x, z, cX, eX, cZ, eZ)) sink.place(x, y, z, Layer.SECONDARY, v.secondary());
                    }
                }
                if (v.between() != null && hit(random, v.density(), x, z, cX, eX, cZ, eZ)) {
                    sink.place(x, y0 + 2 + random.nextInt(2), z, Layer.BETWEEN, v.between());
                }
                if (v.primary() != null) {
                    for (int y = y0 + 3; y < y0 + 6; y++) {
                        if (hit(random, v.density(), x, z, cX, eX, cZ, eZ)) sink.place(x, y, z, Layer.PRIMARY, v.primary());
                    }
                }
                if (v.sporadic() != null && hit(random, v.density(), x, z, cX, eX, cZ, eZ)) {
                    sink.place(x, y0 - 1 + random.nextInt(7), z, Layer.SPORADIC, v.sporadic());
                }
            }
        }
    }

    /** Проверка GT5: ближе к центру по Z или по X — выше шанс. Порядок вызовов Random как в оригинале (короткое замыкание ||). */
    private static boolean hit(Random r, int density, int x, int z, int cX, int eX, int cZ, int eZ) {
        int zSpread = Math.max(Math.abs(cZ - z), Math.abs(eZ - z)) / density;
        if (r.nextInt(Math.max(1, zSpread)) == 0) return true;
        int xSpread = Math.max(Math.abs(cX - x), Math.abs(eX - x)) / density;
        return r.nextInt(Math.max(1, xSpread)) == 0;
    }
}
