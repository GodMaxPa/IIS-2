# Заметки о переносе

Каждое отличие от GT5-II / ИИС 1.7.10 фиксируется здесь: что было, что стало, почему.

## Энергия

### Уровни напряжения (`ru.iis.energy.VoltageTier`)
- Источник: `gregtech.api.enums.GT_Values.V`, `VN`, `VOLTAGE_NAMES`.
- Значения 1:1. В оригинале массив дополнен повторами MAX до 16 элементов; у нас один MAX.

## Машины

### Разгон одноблочной машины (`ru.iis.machine.Overclock`)
- Источник: `GT_MetaTileEntity_BasicMachine#calculateOverclockedNess(int, int)`.
- Формула 1:1, включая особую ветку для рецептов ≤ 16 EU/t.
- **Отличие:** длительность ограничена снизу 1 тиком. В GT5 целочисленное деление могло дать 0 тиков.

## Оригинальные моды
См. `ORIGINAL_MODS.md`.

## Мир

### Рудные жилы (исследование, реализации ещё нет)
- Источники: `GT_Worldgenerator.WorldGenContainer#run`, `GT_Worldgen_GT_Ore_Layer#executeWorldgen`, настройки сборки `config/GregTech/WorldGeneration.cfg`.
- Данные ИИС извлечены в `reference/original/iis_ore_veins.json` (37 жил) скриптом `tools/extract/gt5_veins.py`.
- Алгоритм описан в `docs/SLICE2_PLAN.md`. Перенос высот — решение владельца (В1).
- Известная особенность оригинала: в `getRandom` выражение `nextLong() >> 2 + 1L` — это сдвиг на 3 (приоритет операторов Java). Для нас неважно: побитовая совместимость с миром 1.7.10 не нужна.
