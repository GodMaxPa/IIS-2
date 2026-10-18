# Передача дел (для следующей сессии ИИ)

Обновлено: 2026-10-18, конец среза 3 «Пар».

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

## Ловушки
- Пакет данных GUI — short: не передавать в `ContainerData` числа больше 32767 (прогресс доменной печи — в тысячных).
- `register_repo_root` в облаке падает из-за пути — не мешает.
- Папка mods у владельца общая для ИИС 1.7.10 и NeoForge 26.1.2 — jar кладём в `forServerClient\mods` и в `Downloads\IIS2`.
- JEI нужен в клиенте владельца отдельно (jar JEI 29.47.0.111 для 26.1.2) — мод без него работает.

## Следующее
Срез 4 «LV»: EU-сеть и кабели с потерями, генераторы, LV-машины, схемы и резина, коксовая печь (D29). Перед началом — вопросы владельцу.
