# Состав оригинальной ИИС и судьба модов

Источник: папка `mods` клиента ИИС 1.19.1 владельца (`%APPDATA%\forServerClient\mods`), просмотрено 2026-10-18.
Отключённые в сборке (`.disabled`) моды не перечислены: IC2 NEI Plugin, JourneyMap, OpenComputers, старые версии RTG.

Обозначения: **A** — переносим / пишем сами · **B** — берём готовый современный мод · **C** — убираем или решаем позже · 🟡 наличие на 26.1 не проверено.

| Мод (1.7.10) | Версия | Роль в ИИС | Судьба |
|---|---|---|---|
| GT_II-edition (GregTech 5-II) | 1.19.1_14 | Ядро сборки | **A** — ядро `iis` |
| GTTweaker, CraftTweaker (IIA-edition), ModTweaker2 | — | Правки рецептов ИИС скриптами | **A** — скрипты конвертируем в рецепты-данные |
| GT_Scanner_Mod (II) | 1.5.9 | Геологоразведка | **A** — `world` |
| industrialcraft-2 (exp, II-edition) | 2.2.828 | Энергия EU, базовые предметы, реакторы | **A** — своя EU и нужные предметы (код IC2 закрыт) |
| IC2NuclearControl (IIS) | 2.4.4a | Мониторинг реакторов | C — решим вместе с реакторами |
| IC2-Crops-Tools | 0.0.2 | Селекция культур IC2 | C |
| GraviSuite | 2.0.3 | Топ-броня и инструменты | **A** — войдёт в «промышленное снаряжение» |
| Railcraft | 9.12.2.0 | Железная дорога, коксовая печь, паровые машины | **A** — ж/д (решение владельца) |
| GalacticraftCore + Planets, MicdoodleCore | 3.0.12.504 | Космос | **A** — космос (решение владельца) |
| SGCraft | 1.9.5 | Звёздные врата | C — может стать частью космоса |
| appliedenergistics2 (II), ExtraCells (II), NotEnoughEnergistics | rv3 | Цифровое хранение | B 🟡 AE2 |
| NotEnoughItems (II), NEIIntegration, neiaddons | — | Просмотр рецептов | B — JEI или EMI 🟡 |
| Waila, WailaHarvestability, WAILAPlugins | — | Подсказка о блоке | B — Jade 🟡 |
| MapWriter (II) | 2.1.21 | Карта | B — JourneyMap / Xaero 🟡 |
| BetterQuesting | 3.0.320 | Квесты | B — FTB Quests или аналог 🟡; контент из Ideal-Industrial-Quests |
| forestry (II), gendustry | 4.2.26 | Пчёлы, деревья | C — владелец: не интересно |
| buildcraft (core, builders, silicon, transport), buildcraft-compat | 7.1.17 | Трубы, строители | C — пересекается с GT |
| EnderIO, EnderCore | 2.3.0 | Машины, трубы | C — пересекается с GT |
| ProjectRed (Base, Compat, Fabrication, Integration, Lighting) | 4.7.0 | Редстоун, логика | C — пересекается с современным редстоуном |
| Flans-Mod, HMG (handmadeguns) | — | Оружие и техника | C → идеи для «промышленного снаряжения» (свои реализации) |
| openmodularturrets | 2.3.7 | Турели | C → возможно, часть снаряжения |
| CustomOreGen | 1.2.24 | Генерация руд | C — заменяется рудными жилами GT |
| undergroundBiomes | 0.8 | Каменные породы | C |
| RTG | 1.1.1.7 | Ландшафт | C — современный ландшафт лучше |
| Chisel, Carpenter's Blocks, BiblioCraft, malisisdoors | — | Строительство, декор | B — современные аналоги 🟡 |
| betterbuilderswands | 0.8.0 | Строительный жезл | B 🟡 |
| ironchest, Jabba | — | Хранение | B 🟡 |
| ChickenChunks | 1.3.4 | Чанклоадеры | B 🟡 |
| InventoryTweaks, Stackie | — | Удобства | B 🟡 |
| Treecapitator, FastLeafDecay | — | Удобства | B 🟡 |
| LootBags | 2.0.17 | Мешки с лутом (награды квестов) | B/C |
| InGameInfoXML | 2.8.1 | HUD | C |
| AromaBackup, Aroma1997Core | — | Бэкапы мира | C — делается средствами сервера |
| keepinginventory | 2.1 | Сохранение инвентаря | C — есть ванильное правило |
| CustomMainMenu, betterloadingscreen, ResourceLoader | — | Оформление | C |
| fastcraft, l4j_jndi_fix, galacticraft_fuel_fix, ctbupdater, AppleCore, bdlib, CodeChickenCore, LunatriusCore, INpureCore, malisiscore, StandardExpansion | — | Технические / библиотеки | C — не нужны на 26.1 |

Классификация B и C — черновик ИИ, владелец может переставить любой мод.
