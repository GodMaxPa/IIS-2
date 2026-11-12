# Передача дел (для следующей сессии ИИ)

Обновлено: 2026-11-06 — после D124. Ветка `feature/bio-fluids-lv3`, работа идёт локально (Claude Code на ПК владельца); push — только по просьбе владельца.

## Где мы
- **Начало книги (D151):** новые главы — таблицы `LANDING` и `BRONZE` в `tools/gen/quests_start.py` (ключ, столбец, строка, задания, зависимости, заголовки, тексты). Зависимость на квест, оставшийся в книге ИИС 1, пишется как `@` + его задание (`@#iis:tools/hammer`). Следующее: так же переписать «Пар и сталь» и «Схема, турбина, помпа» (добавить их в `CHAPTERS`), затем простой режим и квесты Био.
- **Проверка прохождения (D150):** после `gen_content.py` и `quests.py` запускать `python -I tools/quest_walk.py --write` (должно быть hard 0) и `python -I tools/recipe_shadow.py` (должно быть 0 can never be picked). Новый машинный рецепт с теми же входами, что у существующего, — различать программируемой схемой (`tools/gen/shadowfix.py` или сразу в генераторе). Следующий пункт плана D149 — новые начальные главы квестов под ИИС 2.
- **План по ответам владельца 13.11 (D149):** 1) автоматическая проверка прохождения Кибера по квестам и исправление тупиков; 2) новые начальные главы квестов под ИИС 2; 3) простой режим Био до LV и квесты Био; 4) материнский корабль — структура Верхнего мира; 5) киберимпланты с HV. Работать длинными автономными заходами.
- **Черновик Био Б-2 (D148):** новые органы — строки в `bio.production.Station` + блок в `BioContent.STATIONS2` + рецепты и вид в `tools/gen/bio_b2.py` (дополняет таблицы `bio_b1.py` до записи). Мутанты — `bio.mutant.Mutants` (вложение данных `iis:bio_mutant` на животном, события взаимодействия и тика). Канал предметов — `bio.block.ItemChannelBlockEntity`. Всё — черновик, числа не утверждены; следующий шаг — ответы владельца на «интервью» 13.11.
- **Диагностика (D147), журнал `docs/DIAGNOSTICS.md`:** эталон рецептов ИИС 1 — `reference/iis1_dump` (как получен — `tools/iis1dump/README.md`). Сверки: `tools/iis1_compare.py --all` (верстак), `tools/iis1_gt_compare.py --write` (машины → `reference/iis1_import.json`), `tools/iis1_conflicts.py --delete-list` (клэши → `reference/iis1_import_skip.json`). Генераторы: `iis1parity.py`, `iis1import.py` (после `iis1fix`). Порядок сборки: `gen_content.py` → `quests.py` (картинки рецептов) → `gradlew build`. Из песочницы Claude Gradle падает на «loopback connection» — нужен `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<папка в проекте>` и запуск вне песочницы. Ветка отправляется на GitHub после каждого этапа (разрешение владельца 11.11).
- **Правки 09.11 (D144):** рецепты ИИС 1 — `tools/gen/iis1fix.py`; квесты: задача «любой из» — предмет `ftbfiltersystem:smart_filter` с компонентом `ftbfiltersystem:filter` (`or(item(a)item(b))`, `item_tag(...)`), картинки рецептов делает `tools/gen/quest_recipes.py`, поэтому `quests.py` надо запускать **до** сборки JAR. Событие ломания блока в NeoForge 26.1 — `level.block.BreakBlockEvent`. Новые инструменты GT — строки `TOOLS` в `gen_content.py`, электрические перечислены в `GtTools.ELECTRIC`.
- **Напыление и новое ванильное (D143):** `tools/gen/modern.py`. Напыление — рецепт ассемблера, выход которого несёт компоненты `max_damage`, `tool`, `lore`; кода нет. Элитрам `GLIDER` снимается в `EarlyGame#weakenStone`, рецепт рамы его возвращает (`!minecraft:lore` снимает подсказку).
- **Начало игры (D142):** ванильные рецепты ИИС 1 — в пакете переопределений (`tools/gen/earlygame.py`), каменные инструменты меняются в `ModifyDefaultComponentsEvent` (блоки брать из `HolderLookup.Provider` инициализатора: реестр уже заморожен), бесконечные жидкости — `CreateFluidSourceEvent`. Щит, не являющийся `ShieldItem`, при блоке поворачивается рукой как меч — отсюда `IClientItemExtensions#applyForgeHandTransform`.
- **Панели Nuclear Control (D141):** `ru.iis.monitor`; строки панели собирает сервер (`PanelBlockEntity#read`) и шлёт клиенту компонентами, текст рисует `PanelRenderer` через `submitText`.
- **Щиты и турели (D140):** `ru.iis.defense`. Щиты — компонент `BlocksAttacks` (доли урона складываются), силовой платит зарядом в `LivingShieldBlockEvent`. Турель ищет цель и стреляет в `TurretBlockEntity#serverTick`, голову рисует `TurretRenderer` мешем `turret_<вид>`. В 26.1 `ClipContext` не принимает null вместо сущности — `CollisionContext.empty()`.
- **Грядки IC2 (D139):** `ru.iis.crop`: культуры читаются из `iis/crops.json` (генератор `tools/gen/crops.py`, числа GT — из GT_CropLoader по `IIS_GT5_SRC`). Номер культуры в блоке — индекс в списке + 2: добавлять культуры только в конец, иначе сохранённые грядки сменят культуру. Органический репликатор — тип `organic_replicator` в `LvMachineType`, логика в `ElectricMachineBlockEntity#replicateSeeds`.
- **Магии нет (D137):** `tools/gen/nomagic.py` переписывает сделки торговцев из ванильного JAR, `world.NoMagic` — добыча и мобы. В NeoForge 26.1 каждый файл в `loot_modifiers` — отдельный модификатор, общего списка нет.
- **Космос, третий этап (D134):** астероиды, ракета 3-го уровня, `space.SpaceMachineBlock(Entity)` — кислород и груз. Проверка полёта: `IIS_SPACE_CHECK=asteroids gradlew runServer`.
- **Машины GT из квестов (D133):** колодец и машины на мускульной силе — `ru.iis.auto.Well*`, `Muscle*`; сканер старателя — `lv.item.ProspectorScannerItem`; ломтерезка — строка в `lv.py` и `LvMachineType`. Текстуры новых обычных машин надо положить в `tools/gen/textures/lv` (`basicmachines/<вид>`, `gui/<окно>.png`), генератор берёт их оттуда.
- **Винт вертолёта, центр выстрела, предметы GT из квестов (D132):** `tools/gen/gtclose.py`, таблицы блоков в `reference/original/gt5_metal_blocks.json`. Полный список недостающего — `quests/REPORT.md`, он теперь без обрезки. В ИИС 1 `EnableZPMandUVBatteries` включена: перед тем как брать ветку GT по умолчанию, смотреть `forServerClient/config/GregTech`.
- **Космос, второй этап (D131):** Марс, ракета 2-го уровня, наквада теперь с руды. Проверка `IIS_SPACE_CHECK=mars gradlew runServer`, доступность до Марса `tools/check_reach.py --no-mars`.
- **Транспорт, магазины, плазма, магазин наград, топливо (D130):** багажники, вездеход, заправщик, патронный станок, `ru.iis.shop`. Снимки и настоящая езда: `-Pequipshots`, строки `climb` в журнале.
- **Оружие, броня, транспорт, вид поездов (D128):** пакет `ru.iis.equipment`, модели в `tools/gen/*_models.py`, съёмка `gradlew runClient -Pequipshots`. Числа и рецепты ждут оценки владельца.
- **Кибер — до UV (D121: кристальные и биосхемы, детали и машины UV, термояд II и III, большой плазменный генератор, буровые III и IV, улучшенный массив; всё закрыто наквадой, D113).** До ZPM **(D112: HSS-E, квантовые схемы до Ultimate, детали и машины ZPM; наквады нет до среза «Космос», D17 и решение владельца D113: корпус ZPM в выживании закрыт).** До LuV **(D110: материалы, схемы до Master, сканер, сборочная линия, детали и машины LuV; `tools/check_reach.py` — проверка, что всё получается от руды).** До IV **(D103: корпус, детали и 33 машины IV, люки и буферы IV, люки MV–IV, материалы IV; D104: имплозионный компрессор, мультиплавильня, пиролизная печь, ректификационная колонна, крекер, большой химреактор, большая газовая турбина; D105: теплообменник, турбина ВД, обрабатывающий массив, угольная яма, чистая комната; D106: нефтяные и рудные буровые, бетонные засыпщики, подземные жидкости; D107: водяные насосы и очистка воды; D108: автоматика, квантовые хранилища, автобур, помпа, настраиваемый трансформатор, зарядники, блоки металлов; D109: большой дизель, отпугиватель, микроволновка; остаток машин — в `docs/PORTING_NOTES.md`).** Раньше: **до EV.** Пар, LV–EV: 35 видов машин на каждом уровне, генераторы, буферы, батарейки, люки, трансформаторы; электрическая доменная печь (катушки до 3600 K), вакуумный морозильник, большие котлы и турбина; биомасса → пластик, кремний, титан, вольфрамосталь, схемы до Elite; 13 покрытий с интерфейсом. Удобства D101: раздельные выходы (ключ / Ctrl + ключ), капсула по иконке жидкости, вкладки и JEI по уровням.
- **Био — первые LV-органы** (с 23.10 без изменений). **Дуо** — не начато; независимость путей держит `PathIndependenceTest` (D77).
- **Железная дорога (D102):** глава квестов «Рельсы и поезда» — пути, стрелки, сцепка, паровоз и электровоз, фидер, сигналы, погрузчики, путеукладчик, тоннельный бур (`ru.iis.rail`, `tools/gen/rail.py`). В игре не проверено.
- **Квесты:** книга ИИС 1 в FTB Quests, часть из 1943 видна (число — в `quests/REPORT.md`) (`tools/gen/quests.py`, `quests/REPORT.md`).
- Владелец в игре проверил загрузку мира, ключ и исправления 19–20.10; друг владельца прошёл первый блок 27.10 (D87). MV, HV, EV, IV (D103), многоблоки и машины D104–D111, D101 и железная дорога D102 в игре не проверены.
- В сборке владельца (папка mods): `iis-0.1.0.jar`, JEI 29.47.0.111, JourneyMap 26.1.2-6.0.10, Jade 26.1.12, MalisisDoorsReborn 1.0.5, RightClickHarvest 4.6.2 + JamLib 2.3.1, GraveStone 1.0.40, FTB Quests 26.1.2.9 + FTB Library 26.1.2.10 + FTB Teams 26.1.2.4 (копии — в `Downloads\IIS2`).
- 266 unit-тестов и все 352 GameTest проходят (на момент D131).
- Что перенесено и чем доказано — `docs/MIGRATION_STATUS.md`; отличия — `docs/PORTING_NOTES.md`; решения D1–D124 — `docs/DECISIONS.md`; отчёты о состоянии — артефакты «Библия», «Кибер», «Био», «Дуо», «ИИС 2 и ИИС 1».

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

Стендовый мир-эталон (D74): `powershell -ExecutionPolicy Bypass -File scripts\stand.ps1` — строит мир сервером разработки и кладёт его в `forServerClient\saves\IIS2_Stand` (прежний — в `iis-backups`). Игра при этом может быть закрыта или открыта на другом мире. Новый вид (D124): вход с доской переходов, 16 залов вдоль аллеи на запад, первый — рабочая сеть. Журнал `build\stand.log`, строки `STAND_CHECK` показывают, что делает сеть. Проверка вида: `scripts\stand.ps1 -DevOnly`, затем `gradlew runClient -Pstandshots`: снимки в `run\screenshots\stand_*.png`.

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
- (D121, 2026-11-06) Команда владельца «GT до конца»: уровень UV и термояд II, III сделаны, всё закрыто наквадой (D113; владелец подтвердил после D121: ждать космос, жилу в Энд не возвращать): `python -I tools/check_reach.py --with=iis:dust_naquadah --all`. Сделано после этого (D122): разведка, сканеры, взрывчатка, репликатор, уровень MAX. D123: предметные трубы. Осталось по GT: ломтерезка и еда, автоматика MAX, органический репликатор (нужны культуры IC2), окраска сетей труб. Сверка квестов: `quests/REPORT.md`, большая часть остатка — AE2, EnderIO, ProjectRed, Марс.
- (D101, 2026-10-28) Книга квестов: `python -I tools/gen/quests.py` после `gen_content.py`, затем скопировать `quests/ftbquests` в `<игра>/config/ftbquests/quests`; что мешает остальным квестам — `quests/REPORT.md`. Переводы FTB — только в папках `lang/<язык>/`. Для проверки в разработке моды FTB лежат в `run/mods` (вне репозитория).
- Ждём проверки владельцем в игре: всё после 20.10, в первую очередь D101.
- Порядок дальше решает владелец: недостающие машины GT LV–EV (сканер, принтер, упаковщик, разборщик и др.) и многоблоки нефти и химии — их ждут скрытые квесты; эпоха IV; железная дорога или космос; награды квестов (монеты GT).
- (D125, 2026-11-06) AE2 и Ender IO: готовые моды плюс правила ИИС 1. Генератор `tools/gen/aeio.py` читает скрипты из `forServerClient/scripts`, список содержимого модов: `reference/original/aeio_mods.json` (`tools/extract/aeio_mods.py <папка с JAR>`). При смене версии мода: обновить номер в `gradle.properties`, перечитать список, запустить генератор и тесты `aeio_*`. Запуск генератора на Windows: `IIS_GT5_TEXTURES=%TEMP%/gt5src/src/main/resources/assets/gregtech/textures`. Для клиента и сервера нужны JAR: AE2, GuideME, Ender IO. Отчёт о непереведённом: `quests/AEIO_REPORT.md`. Следующее: ExtraCells, проверка в игре.
- (D126, D127, 2026-11-06) Окна: `gradlew runClient -Pguishots` снимает все виды окон в `run/screenshots`, пишет `run/gui_report.txt`, `python tools/gui_audit.py` сверяет слоты с текстурами. После новых машин и правки окон запускать обязательно; бронзовые и стальные окна аудит отмечает ложно (другие цвета рамок). Рисунки снаряжения — текстом в `tools/gen/gear_art.py`. Новые моды — `tools/gen/moremods.py`, номера в `gradle.properties`, в клиент ставить JAR из того же Modrinth. Inventory Profiles Next не ставить: кнопки поверх окон.
