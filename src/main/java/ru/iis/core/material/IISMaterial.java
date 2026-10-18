package ru.iis.core.material;

import java.util.List;

/**
 * Материал IIS (аналог gregtech.api.enums.Materials). Данные — из /iis/materials.json,
 * который генерирует tools/gen/gen_ores.py из справочника GT5-II.
 *
 * @param id           ID без пространства имён, например "chalcopyrite"
 * @param color        RGB цвета материала (как в GT5)
 * @param iconSet      набор текстур GT5 (METALLIC, DULL, ...)
 * @param harvestLevel уровень добычи руды GT5 (0..7): 0-1 камень, 2 железо, 3+ алмаз
 * @param hosts        породы, в которых встречается руда: stone, deepslate, netherrack, end_stone
 */
public record IISMaterial(String id, String gtName, String en, String ru, int color, String iconSet,
                          int harvestLevel, List<String> hosts) {
    public IISMaterial {
        hosts = List.copyOf(hosts);
    }
}
