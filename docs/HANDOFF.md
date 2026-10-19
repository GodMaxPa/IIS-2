# Передача дел (для следующей сессии ИИ)

Обновлено: 2026-10-20 — **чекпоинт перед переходом владельца на локальную работу** (Claude Code на его ПК).
Последний коммит чекпоинта — см. `git log`; всё запушено в https://github.com/GodMaxPa/IIS-2.

## Где мы
- Готово (облако, тесты): срезы 1–4 (руды, инструменты, пар, LV), электрическая доменная печь, загрязнение, техобслуживание,
  LV II (центрифуга, просеиватель, сепаратор, автоклав, пильный станок, консервирующий станок), батарейки LV (натрий/кадмий/литий),
  покрытия GT на электроблоках (11 видов), мультиметр, валка деревьев, ключ с сеткой GT, слой JourneyMap «сетка рудных чанков».
- Владелец в игре проверил: исправления ключа/креозота/доменной печи (2026-10-19), ключ с сеткой. Остальное ещё не смотрел.
- В сборке владельца (папка mods): `iis-0.1.0.jar`, JEI 29.47.0.111, JourneyMap 26.1.2-6.0.10.
- 40 GameTest + unit-тесты + smoke (selftest) проходят.
- Что перенесено и чем доказано — `docs/MIGRATION_STATUS.md`; отличия — `docs/PORTING_NOTES.md`; решения D1–D63 — `docs/DECISIONS.md`.

## План (согласован с владельцем 2026-10-20)
1. Ячейки GT (пустая оловянная + с жидкостями/газами) и электролизёр → разложение пылей с газами, натрий/хлор/водород.
2. Оставшиеся покрытия: регулятор и фильтр жидкости; покрытия на паровых машинах, трубах, люках.
3. Химия LV: химреактор, химванна, экстрактор/отвердитель жидкостей, расплавы (убрать временные замены припоя/резины).
4. Электроинструменты LV (бур, цепная пила, электроключ).
5. MV: детали MV, термоцентрифуга, вакуумный морозильник, горячие слитки.
6. Биопуть (параллельно; образцы — `docs/BIO_PATH.md`).

## Работа локально (Windows, ПК владельца)
Ничего не ставить без согласия владельца. Нужно (он ставит сам): Git, **JDK 25** (например Temurin 25), Python 3.11+.
```bat
git clone https://github.com/GodMaxPa/IIS-2.git
cd IIS-2
python -I tools\gen\gen_content.py        :: пересобрать данные (идемпотентно, результат уже в репозитории)
gradlew.bat build                         :: компиляция + unit-тесты; jar в build\libs\iis-0.1.0.jar
gradlew.bat runGameTestServer             :: все GameTest
gradlew.bat runClient                     :: клиент разработки с JEI (JourneyMap: gradlew.bat runClient -Pshowcase)
```
- Скрипты `scripts/*.sh` (smoke, gametest, showcase со скриншотами) рассчитаны на Linux/облако (bash, xvfb). Локально вместо них — `runServer`/`runGameTestServer`/`runClient` и глаза владельца.
- Сборку для игры владельца копировать в `C:\Users\Godkiller\AppData\Roaming\forServerClient\mods\iis-0.1.0.jar` (и `Downloads\IIS2`). Папка mods общая с ИИС 1.7.10.
- Эталон GT5-II (исходники) в репозиторий не входит: https://github.com/IdealIndustrial/GT5Unofficial, коммит `00021e3` — склонировать рядом, если нужно сверять код. Конфиги и скрипты сборки ИИС 1.19.1 (`GregTech.lang.ru`, `config/GregTech/*.cfg`, `scripts/*.zs`) — у владельца (папка «ИИС v1.19.1»); выжимки уже лежат в `reference/original`.
- В облаке JDK подключался через `source /home/claude/tools/env.sh` — локально это не нужно (JAVA_HOME на JDK 25).

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
