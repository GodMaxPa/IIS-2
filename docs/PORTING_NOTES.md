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

### Рудные жилы (`ru.iis.world.vein`, `ru.iis.world.ore`) — реализовано
- Источники: `GT_Worldgenerator.WorldGenContainer#run`, `GT_Worldgen_GT_Ore_Layer#executeWorldgen`, настройки сборки `config/GregTech/WorldGeneration.cfg`.
- Данные ИИС извлечены в `reference/original/iis_ore_veins.json` (37 жил) скриптом `tools/extract/gt5_veins.py`.
- Алгоритм описан в `docs/SLICE2_PLAN.md`. Перенос высот — решение владельца (В1).
- Известная особенность оригинала: в `getRandom` выражение `nextLong() >> 2 + 1L` — это сдвиг на 3 (приоритет операторов Java). Для нас неважно: побитовая совместимость с миром 1.7.10 не нужна.
- Реализация: `VeinFeature` в шаге `underground_ores`; т.к. писать можно только в текущий чанк, жилы соседних квадратов пересчитываются детерминированно и кешируются (LRU 512).
- **Отличие:** руда ставится только вместо природной породы (теги `stone/deepslate_ore_replaceables`, `base_stone_nether`, эндерняк). В GT5 руда заменяла камень и несколько пород своих модов.
- **Отличие:** порода определяет вариант блока (камень/глубинный сланец/незерак/эндерняк). В GT5 были варианты для гранитов, базальта и мрамора GT.
- **Отличие:** выбор жилы делается только среди жил своего измерения (в GT5 — повторные попытки по общему списку). Распределение по весам то же.
- Ванильные руды убраны biome modifier'ами; большие жилы нового генератора — `ore_veins_enabled=false` в noise_settings overworld/large_biomes/amplified.
- Малые руды (`ore.small.*`) — ещё не сделаны.

## Материалы и предметы (`ru.iis.core`)
- Источник свойств — `MaterialProperties.cfg` сборки (D20), форм — `OrePrefixes` GT5 (биты `MaterialTypes`: 1 пыль, 2 металл, 4 кристалл, 8 руда, 64 инструменты).
- Названия — из `GregTech.lang.ru` сборки по ключам `gt.metaitem.01/02.<индекс формы*1000+ID>`.
- **Не перенесено пока:** gemChipped/Flawed/Flawless/Exquisite, ingotDouble+, plateDouble+, foil, gear, spring, wireFine, crushedCentrifuged, ingotHot.

## Инструменты (`ru.iis.tool`)
- Типы и урон — `GT_Tool_*` (block/craft damage), прочность — `100 × ToolDurability` GT, переведено в единицы по 50 (D25).
- Рецепты — `ProcessingToolHead/ToolOther`, ступки — `GT_MetaGenerated_Tool_01` (кремень/бронза/железо).
- **Отличие:** в GT5 инструмент — один мета-предмет с NBT материалов; у нас отдельный предмет на пару (инструмент, материал).
- **Отличие:** молот при добыче превращает только блоки руд (в GT5 — всё, у чего есть рецепт кузнечного молота: камень, булыжник, гравий).
- **Не перенесено пока:** киянка, кусачки, монтировка, лом, ножницы, электроинструменты.

## Ручная переработка
- Печь — `ProcessingOre` (руда: множитель плавки, «богатые» руды незерака/эндерняка ×2) и `ProcessingOreSmelting` (дроблёная руда: 10 самородков, смешанная — 6, ИИС `MixedOreOnlyYieldsTwoThirdsOfPureOre=true`).
- Ступка, молот, напильник, пила, пыли — `ProcessingIngot/Plate/Stick/Bolt/Screw/Dust`; ИИС `NuggetsFix.zs`: самородок + ступка → крошечная пыль.
- Котёл — `GT_MetaGenerated_Item_01#onEntityItemUpdate`.
- Бронза — `GT_CraftingRecipeLoader` с `NerfDustCrafting=true` (3 медных + 1 оловянная → 3 бронзовые).

## Малые руды
- `GT_Worldgen_GT_Ore_SmallPieces` + конфиг ИИС (34 вида), высоты по D11 с обрезкой до −64..319.
- Выпадение — `GT_TileEntity_Ores#getDrops` (мета ≥ 16000): кристалл/дроблёная руда/грязная пыль по весам GT, 1/3 — пыль породы.
- **Отличие:** удача (Fortune) не увеличивает выпадение; вместо пыли глубинного сланца выпадает каменная.
