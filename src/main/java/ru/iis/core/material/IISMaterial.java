package ru.iis.core.material;

import java.util.List;

/**
 * Материал IIS (аналог gregtech.api.enums.Materials). Данные — из /iis/materials.json,
 * который генерирует tools/gen/gen_content.py из справочника GT5-II и конфигов сборки ИИС.
 *
 * @param id             ID без пространства имён, например "chalcopyrite"
 * @param color          RGB цвета материала (как в GT5)
 * @param iconSet        набор текстур GT5 (METALLIC, DULL, ...)
 * @param harvestLevel   уровень добычи руды GT5 (0..7): 0-1 камень, 2 железо, 3+ алмаз
 * @param hosts          породы, в которых встречается руда: stone, deepslate, netherrack, end_stone
 * @param smallHosts     породы, в которых встречаются малые руды (вкрапления)
 * @param formula        химическая формула для подсказки (может быть null)
 * @param directSmelting во что плавится руда (GT5 mDirectSmelting)
 * @param oreMultiplier  множитель выхода руды (GT5 mOreMultiplier)
 */
public record IISMaterial(String id, String gtName, String en, String ru, int color, String iconSet,
                          int harvestLevel, List<String> hosts, List<String> smallHosts, String formula, String directSmelting,
                          int oreMultiplier, List<String> subTags) {
    public IISMaterial {
        hosts = List.copyOf(hosts);
        smallHosts = List.copyOf(smallHosts);
        subTags = List.copyOf(subTags);
    }

    public boolean has(String subTag) {
        return subTags.contains(subTag);
    }
}
