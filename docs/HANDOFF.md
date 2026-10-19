# Передача дел (для следующей сессии ИИ)

Обновлено: 2026-10-19, срез 4 «LV» (ядро + первые машины).

## Где мы
- Срезы 1–3 сделаны в облаке. Владелец проверил в игре только загрузку мода (срез 2). Срез 3 он ещё не видел.
- Что перенесено и чем доказано — `docs/MIGRATION_STATUS.md`. Отличия от оригинала — `docs/PORTING_NOTES.md`.

## Как проверить, что всё живо (≈10 минут)
```bash
source /home/claude/tools/env.sh        # JDK 25 (в облаке Claude)
python3 -I tools/gen/gen_content.py     # пересобрать данные (должно закончиться строкой со steam: {...})
./gradlew build                          # компиляция + unit-тесты
scripts/smoke-server.sh                  # сервер + /iis selftest (+ скан руд)
scripts/gametest.sh                      # GameTest: котлы, машины, трубы, доменная печь
scripts/showcase.sh steam                # скриншот всех паровых машин (build/showcase/steam.png)
```

## Устройство среза 3
- Чистая логика без Minecraft: `ru.iis.machine.logic` (`BoilerLogic`, `SteamMachineLogic`, `BbfStructure`) — тесты в `src/test/.../machine/logic`.
- Блоки/блок-сущности/меню/экраны: `ru.iis.machine.{block,menu,client}`, регистрация — `IISMachines`.
- Рецепты машин: тип `iis:<карта>` (`GtRecipe`: `inputs` с количеством, `outputs` с шансом в 1/10000, `duration`, `eut`), генерирует `tools/gen/steam.py`.
- Рецепты отправляются клиенту (`OnDatapackSyncEvent.sendRecipes`) — для JEI.
- GameTest: `ru.iis.gametest.IISGameTests`, площадка `data/iis/structure/floor9.nbt` (создаёт `tools/gen/gametest_structures.py`).

## Устройство среза 4 (LV)
- Чистая логика: `ru.iis.energy.logic` (`EnergyNet`, `EnergyStorage`, `CableState`, `GeneratorLogic`), `machine.logic.ElectricMachineLogic`; связь с миром — `energy.WorldEnergy`.
- Блоки: `ru.iis.lv.block` (провода `CableBlock`, база `ElectricBlockEntity` с опасностями и взрывами, машины, генераторы, буфер, трансформатор, коксовая печь, каучуковое бревно), регистрация — `ru.iis.lv.LvContent`.
- Подключение проводов: блок реализует `EnergyConnectable`, блок-сущность — `EuNode` (D46).
- Взрывы — в конце тика (`lv.Deferred`, D48). Опасности выключаются в `serverconfig/iis-server.toml` (`IISConfig`, D47).
- Данные — `tools/gen/lv.py` (вызывается из `gen_content.py`), каталоги в Java сверяет `LvDataTest`.

## Многоблочные машины и загрязнение
- `ru.iis.multi`: логика без Minecraft — `logic.BlastLogic` (уровень, разгон, нагрев), `logic.Maintenance`; блоки — `HatchBlock` (7 видов люков), `EbfBlockEntity` (форма, рецепт, энергия, выхлоп); регистрация — `MultiContent`; данные — `tools/gen/ebf.py`.
- `ru.iis.pollution`: `PollutionLogic` (числа GT), `PollutionData` (SavedData по чанкам), `Pollution` (обход раз в 1200 тиков, эффекты, `add/get`).
- Концепция владельца — `docs/CONCEPT.md`, сверка с проектом — `docs/CONCEPT_RECONCILE.md`.

## Ловушки
- Скриншот-сервер иногда сохраняет мир без `world_gen_settings.dat` — клиент пишет «Ошибки в наборах данных». Просто перезапустить `scripts/showcase.sh`.
- Не запускать gradle (даже compileJava), пока идёт `scripts/showcase.sh`: клиент берёт классы из build и падает (`ClassNotFoundException`).
- `FluidStack.CODEC` нельзя использовать в рецептах — жидкие выходы через `FluidOutput` (D49).
- Пакет данных GUI — short: не передавать в `ContainerData` числа больше 32767 (прогресс доменной печи — в тысячных).
- `register_repo_root` в облаке падает из-за пути — не мешает.
- Папка mods у владельца общая для ИИС 1.7.10 и NeoForge 26.1.2 — jar кладём в `forServerClient\mods` и в `Downloads\IIS2`.
- JEI нужен в клиенте владельца отдельно (jar JEI 29.47.0.111 для 26.1.2) — мод без него работает.

## Следующее
Остальные LV-машины и провода, мультиметр (наглядность), коксовые варианты доменной печи; затем первый срез биопути (D39). Ждём отзыв владельца по LV в игре.
