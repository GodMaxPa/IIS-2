# Справочные данные оригинала

Не игровые данные мода, а **эталон** для переноса. Генерируются скриптами из `tools/extract/`.

| Файл | Источник | Скрипт |
|---|---|---|
| `original/gt5_materials.json` | GT5-II `gregtech/api/enums/Materials.java` (коммит 00021e3, 2026-07-12) | `gt5_materials.py` |
| `original/iis_ore_veins.json` | Сборка ИИС 1.19.1, `config/GregTech/WorldGeneration.cfg` | `gt5_veins.py` |
| `original/iis_small_ores.json` | Сборка ИИС 1.19.1, `config/GregTech/WorldGeneration.cfg` | `gt5_veins.py` |
| `original/iis_material_properties.json` | Сборка ИИС 1.19.1, `config/GregTech/MaterialProperties.cfg` | `gt5_material_props.py` |
| `original/gt5_lang_ru_materials.json` | Сборка ИИС 1.19.1, `GregTech.lang.ru` | `gt5_lang_ru.py` |
| `original/gt5_elements.txt` | GT5-II `Element.java` (протоны, нейтроны) | вручную, однострочник в истории |
