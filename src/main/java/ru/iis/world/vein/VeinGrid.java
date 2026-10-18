package ru.iis.world.vein;

import java.util.List;
import java.util.Random;

/**
 * Сетка жил GT5: мир разбит на квадраты 3x3 чанка, в центральном чанке каждого квадрата — одна жила.
 * Перенесено из GT_Worldgenerator.WorldGenContainer (getVeinCenterCoordinate, getRandom, выбор по весу).
 */
public final class VeinGrid {
    private VeinGrid() {}

    /**
     * Координата центрального чанка квадрата 3x3, которому принадлежит чанк {@code chunk}.
     * Формула GT5 даёт центры ..., -5, -2, 1, 4, ...: квадраты [-3..-1], [0..2], [3..5] и т.д.
     */
    public static int centerChunk(int chunk) {
        int c = chunk + (chunk < 0 ? 1 : 3);
        return c - c % 3 - 2;
    }

    /** Детерминированный генератор для квадрата: зависит только от сида мира и координат центра. */
    public static Random randomFor(long worldSeed, int centerChunkX, int centerChunkZ) {
        Random seeds = new Random(worldSeed);
        long xSeed = seeds.nextLong() >> 3; // в GT5: nextLong() >> 2 + 1L, т.е. сдвиг на 3
        long zSeed = seeds.nextLong() >> 3;
        long chunkSeed = (xSeed * centerChunkX + zSeed * centerChunkZ) ^ worldSeed;
        return new Random(new Random(chunkSeed).nextInt());
    }

    /**
     * Выбор жилы по весу (как в GT5: случайное число в [0, сумма весов), вычитаем веса по порядку,
     * первая жила, на которой остаток стал ≤ 0). Возвращает null, если список пуст или все веса 0.
     * Отличие от GT5: там выбор шёл из общего списка с повторными попытками для неподходящих измерений;
     * мы заранее отфильтровываем жилы по измерению — распределение по весам то же.
     */
    public static VeinDefinition pick(List<VeinDefinition> candidates, Random random) {
        int total = 0;
        for (VeinDefinition v : candidates) total += v.weight();
        if (total <= 0) return null;
        int r = random.nextInt(total);
        for (VeinDefinition v : candidates) {
            r -= v.weight();
            if (r <= 0 && v.weight() > 0) return v;
        }
        // r был в [0,total), значит последняя ненулевая жила гарантированно достигнет r < 0
        throw new IllegalStateException("unreachable");
    }
}
