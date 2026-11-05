# Передача дел (для следующей сессии ИИ)

Обновлено: 2026-11-06 — после D116. Ветка `feature/bio-fluids-lv3`, работа идёт локально (Claude Code на ПК владельца); push — только по просьбе владельца.

## Где мы
- **Кибер — до ZPM (D112: HSS-E, квантовые схемы до Ultimate, детали и машины ZPM; наквады нет до среза «Космос», D17 и решение владельца D113: корпус ZPM в выживании закрыт).** До LuV **(D110: материалы, схемы до Master, сканер, сборочная линия, детали и машины LuV; `tools/check_reach.py` — проверка, что всё получается от руды).** До IV **(D103: корпус, детали и 33 машины IV, люки и буферы IV, люки MV–IV, материалы IV; D104: имплозионный компрессор, мультиплавильня, пиролизная печь, ректификационная колонна, крекер, большой химреактор, большая газовая турбина; D105: теплообменник, турбина ВД, обрабатывающий массив, угольная яма, чистая комната; D106: нефтяные и рудные буровые, бетонные засыпщики, подземные жидкости; D107: водяные насосы и очистка воды; D108: автоматика, квантовые хранилища, автобур, помпа, настраиваемый трансформатор, зарядники, блоки металлов; D109: большой дизель, отпугиватель, микроволновка; остаток машин — в `docs/PORTING_NOTES.md`).** Раньше: **до EV.** Пар, LV–EV: 35 видов машин на каждом уровне, генераторы, буферы, батарейки, люки, трансформаторы; электрическая доменная печь (катушки до 3600 K), вакуумный морозильник, большие котлы и турбина; биомасса → пластик, кремний, титан, вольфрамосталь, схемы до Elite; 13 покрытий с интерфейсом. Удобства D101: раздельные выходы (ключ / Ctrl + ключ), капсула по иконке жидкости, вкладки и JEI по уровням.
- **Био — первые LV-органы** (с 23.10 без изменений). **Дуо** — не начато; независимость путей держит `PathIndependenceTest` (D77).
- **Железная дорога (D102):** глава квестов «Рельсы и поезда» — пути, стрелки, сцепка, паровоз и электровоз, фидер, сигналы, погрузчики, путеукладчик, тоннельный бур (`ru.iis.rail`, `tools/gen/rail.py`). В игре не проверено.
- **Квесты:** книга ИИС 1 в FTB Quests, часть из 1943 видна (число — в `quests/REPORT.md`) (`tools/gen/quests.py`, `quests/REPORT.md`).
- Владелец в игре проверил загрузку мира, ключ и исправления 19–20.10; друг владельца прошёл первый блок 27.10 (D87). MV, HV, EV, IV (D103), многоблоки и машины D104–D111, D101 и железная дорога D102 в игре не проверены.
- В сборке владельца (папка mods): `iis-0.1.0.jar`, JEI 29.47.0.111, JourneyMap 26.1.2-6.0.10, Jade 26.1.12, MalisisDoorsReborn 1.0.5, RightClickHarvest 4.6.2 + JamLib 2.3.1, GraveStone 1.0.40, FTB Quests 26.1.2.9 + FTB Library 26.1.2.10 + FTB Teams 26.1.2.4 (копии — в `Downloads\IIS2`).
- 205 unit-тестов и все 258 GameTest проходят.
- Что перенесено и чем доказано — `docs/MIGRATION_STATUS.md`; отличия — `docs/PORTING_NOTES.md`; решения D1–D116 — `docs/DECISIONS.md`; отчёты о состоянии — артефакты «Библия», «Кибер», «Био», «Дуо», «ИИС 2 и ИИС 1».

## План (согласован с владельцем 2026-10-20) — пункты 1–5 выполнены к 26.10 (D76–D81), дальше шли просьбы владельца D82–D101
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

Полный генератор контента локально (D76): нужен выборочный клон текстур GT5 (`git clone --filter=blob:none --no-checkout https://github.com/IdealIndustrial/GT5Unofficial.git`, `git sparse-checkout set src/main/resources/assets/gregtech/textures`, `git checkout 00021e3`), затем `IIS_GT5_TEXTURES=<клон>/src/main/resources/assets/gregtech/textures python -I tools/gen/gen_content.py`. Результат совпадает с облачным; файлы пишутся с LF.

Стендовый мир-эталон (D74): `powershell -ExecutionPolicy Bypass -File scripts\stand.ps1` — строит мир сервером разработки и кладёт его в `forServerClient\saves\IIS2_Stand` (прежний — в `iis-backups`). Игра при этом может быть закрыта или открыта на другом мире.

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
- GameTest-сервер не загружает измерения из датапаков: `getLevel(MOON)` там `null`. Полёт и генерацию Луны проверяет `IIS_SPACE_CHECK=1 gradlew runServer` (класс `SpaceLiveCheck`, ищи `SPACE_CHECK PASSED`) (D115).
- Новая рамка — это ещё и строка в `MultiContent.FRAME_MATERIALS`. Если блока нет, ломается весь тег `mineable/wrench`: упадёт посторонний тест ключа, причина — в журнале «Couldn't parse» (D112).
- Тесты рецептов проверяют один шаг и не видят разрыва цепочки. После нового контента запускать `python -I tools/check_reach.py --all`: так найдены кованое железо, кольца, кабели IV и морозильник (D110).
- Скриншот-сервер иногда сохраняет мир без `world_gen_settings.dat` — клиент пишет «Ошибки в наборах данных». Просто перезапустить `scripts/showcase.sh`.
- Не запускать gradle (даже compileJava), пока идёт `scripts/showcase.sh`: клиент берёт классы из build и падает (`ClassNotFoundException`).
- `FluidStack.CODEC` нельзя использовать в рецептах — жидкие выходы через `FluidOutput` (D49).
- Пакет данных GUI — short: не передавать в `ContainerData` числа больше 32767 (прогресс доменной печи — в тысячных).
- `register_repo_root` в облаке падает из-за пути — не мешает.
- Папка mods у владельца общая для ИИС 1.7.10 и NeoForge 26.1.2 — jar кладём в `forServerClient\mods` и в `Downloads\IIS2`.
- JEI нужен в клиенте владельца отдельно (jar JEI 29.47.0.111 для 26.1.2) — мод без него работает.

## Следующее
- (D101, 2026-10-28) Книга квестов: `python -I tools/gen/quests.py` после `gen_content.py`, затем скопировать `quests/ftbquests` в `<игра>/config/ftbquests/quests`; что мешает остальным квестам — `quests/REPORT.md`. Переводы FTB — только в папках `lang/<язык>/`. Для проверки в разработке моды FTB лежат в `run/mods` (вне репозитория).
- Ждём проверки владельцем в игре: всё после 20.10, в первую очередь D101.
- Порядок дальше решает владелец: недостающие машины GT LV–EV (сканер, принтер, упаковщик, разборщик и др.) и многоблоки нефти и химии — их ждут скрытые квесты; эпоха IV; железная дорога или космос; награды квестов (монеты GT).
