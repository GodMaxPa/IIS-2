package ru.iis.world.vein;

/**
 * Перенос высот жил из мира 1.7.10 (руды 5..130) в современный мир (-64..320).
 * Решение владельца D11: новая_Y = старая_Y * 1.6 - 64.
 * 5 -> -56 (глубинный сланец), 40 -> 0, 80 -> 64, 130 -> 144 (холмы и горы).
 */
public final class HeightMapping {
    private HeightMapping() {}

    public static final double SCALE = 1.6;
    public static final int OFFSET = -64;

    public static int stretch(int legacyY) {
        return (int) Math.round(legacyY * SCALE) + OFFSET;
    }

    /** Переносит обе границы; минимальный зазор как в GT5 обеспечивает {@link VeinDefinition}. */
    public static VeinDefinition stretch(VeinDefinition legacy) {
        return new VeinDefinition(legacy.name(), stretch(legacy.minY()), stretch(legacy.maxY()), legacy.weight(),
                legacy.density(), legacy.size(), legacy.primary(), legacy.secondary(), legacy.between(),
                legacy.sporadic(), legacy.dimensions());
    }
}
