package ru.iis.world.vein;

import java.util.ArrayList;
import java.util.List;

/**
 * Сетка рудных чанков для карты: квадраты 3×3 чанка (VeinGrid), в центральном чанке квадрата — жила GT5.
 * Чистая логика без Minecraft (покрыта JUnit).
 */
public final class OreGridLayout {
    private OreGridLayout() {}

    /** Квадрат: центральный (рудный) чанк и границы в чанках включительно. */
    public record Region(int centerX, int centerZ) {
        public int minChunkX() { return centerX - 1; }
        public int maxChunkX() { return centerX + 1; }
        public int minChunkZ() { return centerZ - 1; }
        public int maxChunkZ() { return centerZ + 1; }
    }

    /** Квадраты в радиусе radius квадратов вокруг чанка (chunkX, chunkZ): (2r+1)² штук. */
    public static List<Region> around(int chunkX, int chunkZ, int radius) {
        int cx = VeinGrid.centerChunk(chunkX), cz = VeinGrid.centerChunk(chunkZ);
        List<Region> out = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) out.add(new Region(cx + 3 * dx, cz + 3 * dz));
        return out;
    }
}
