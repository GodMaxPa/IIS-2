package ru.iis.world.vein;

import java.util.Objects;
import java.util.Set;

/**
 * Описание рудной жилы GT5 (аналог GT_Worldgen_GT_Ore_Layer).
 * Материалы задаются строковыми ID материалов IIS (например "iis:chalcopyrite"); null — слой отсутствует.
 *
 * @param minY      нижняя граница высоты низа жилы (уже в координатах нового мира)
 * @param maxY      верхняя граница; низ жилы выбирается в [minY, maxY - 5)
 * @param weight    вес при случайном выборе жилы
 * @param density   плотность: чем больше, тем плотнее руда у краёв
 * @param size      насколько жила может выступать за 16 блоков центрального чанка
 */
public record VeinDefinition(String name, int minY, int maxY, int weight, int density, int size,
                             String primary, String secondary, String between, String sporadic,
                             Set<String> dimensions) {
    public VeinDefinition {
        Objects.requireNonNull(name);
        if (weight < 0) throw new IllegalArgumentException(name + ": weight < 0");
        if (density < 1) throw new IllegalArgumentException(name + ": density < 1");
        if (size < 1) throw new IllegalArgumentException(name + ": size < 1");
        // Как в GT5: maxY не меньше minY + 5, чтобы диапазон выбора был непустым
        if (maxY < minY + 6) maxY = minY + 6;
        dimensions = Set.copyOf(dimensions);
    }
}
