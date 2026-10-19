# Статус переноса (матрица адаптации)

Эталон: GT5-II `IdealIndustrial/GT5Unofficial` @ `00021e3` + конфиги и скрипты сборки ИИС 1.19.1 (см. AGENTS.md).
Уровни доказательств: **T** — unit-тесты, **G** — GameTest (машины в мире), **S** — `/iis selftest` на сервере,
**C** — скриншот клиента в облаке, **O** — проверено владельцем в игре, **—** — не проверено.

| Подсистема | Оригинал | Наш код | Статус | Доказательства |
|---|---|---|---|---|
| Уровни напряжения, разгон | `GT_Values.V`, `BasicMachine#calculateOverclockedNess` | `energy.VoltageTier`, `machine.Overclock` | перенесено | T, G |
| Материалы и формы | `Materials`, `OrePrefixes`, `MaterialProperties.cfg` | `core.material`, `tools/gen/gen_content.py` | частично (без gemFlawed.., foil, gear, wireFine…) | T, S, C |
| Жилы и руды | `GT_Worldgen_GT_Ore_Layer`, `WorldGeneration.cfg` | `world.vein`, `world.ore` | перенесено (без космоса) | T, S, C, O (загрузка) |
| Малые руды | `GT_Worldgen_GT_Ore_SmallPieces` | `VeinFeature`, `OreBlocks` | перенесено (без удачи) | S |
| Инструменты GT | `GT_Tool_*`, `GT_MetaGenerated_Tool_01` | `tool.*` | частично (без электроинструментов, киянки, лома…) | S, C |
| Ручная переработка | `ProcessingOre/Ingot/...`, котёл | рецепты + `MaterialItem` | перенесено | S |
| Пар: жидкость | `GT_ModHandler.getSteam` | `fluid.IISFluids` | перенесено | G |
| Малые котлы (4) | `GT_MetaTileEntity_Boiler_*` | `machine.logic.BoilerLogic`, `BoilerBlockEntity` | перенесено | T, G, C |
| Паровые машины (6×2) | `machines/steam/*`, `BasicMachine_Bronze` | `SteamMachineLogic`, `SteamMachineBlockEntity` | перенесено (выход всегда сзади) | T, G, C |
| Карты рецептов GT | `GT_Recipe_Map` | `machine.recipe.*`, `tools/gen/steam.py` | 6 карт, 1382 рецепта | S, G |
| Жидкостные трубы | `GT_MetaPipeEntity_Fluid` | `PipeBlock`, `PipeBlockEntity` | бронза/сталь S/M/L | G, C |
| Кирпичная доменная печь | `GT_MetaTileEntity_PrimitiveBlastFurnace` | `BbfStructure`, `BbfBlockEntity` | перенесено | T, G, C |
| Огнеупорная цепочка | `GT_CraftingRecipeLoader`, `GT_MachineRecipeLoader` | `tools/gen/steam.py` | перенесено | S |
| Просмотр рецептов | NEI | JEI-плагин `machine.compat.jei` | 14 категорий (с жидкостями) + печи как станции плавки | C |
| Загрязнение | `GT_Pollution` | `ru.iis.pollution` | перенесено (D53) | T, G (котёл) |
| EU: запас, приём, выдача, взрывы | `BaseMetaTileEntity` | `energy.logic.EnergyStorage`, `lv.block.ElectricBlockEntity` | перенесено (взрыв в конце тика, D48) | T, G |
| Провода и кабели | `GT_MetaPipeEntity_Cable`, `makeWires` | `energy.logic.EnergyNet`, `WorldEnergy`, `lv.block.CableBlock` | 5 материалов × 1x/2x/4x, провод и кабель | T, G, C |
| Опасности (дождь, вода, огонь, гроза, удар током, сгорание, wirefire) | `BaseMetaTileEntity#updateEntity`, `GT_Utility#applyElectricityDamage` | `ElectricBlockEntity`, `CableBlock` | перенесено; переключатели в serverconfig (D47) | G (вода, перенапряжение, сгорание, удар) |
| LV-машины (13) | `BasicMachine_GT_Recipe`, `GT_Loader_MetaTileEntities` | `machine.logic.ElectricMachineLogic`, `lv.block.ElectricMachineBlockEntity` | дробитель, печь, печь сплавов, компрессор, экстрактор, молот, волочильный, гибочный, токарный, сборщик, смеситель, поляризатор, рудопромывка | T, G, C |
| Генераторы LV (3) | `BasicGenerator`, `SteamTurbine`, `DieselGenerator`, `GasTurbine` | `energy.logic.GeneratorLogic`, `lv.block.GeneratorBlockEntity` | перенесено; жидкого топлива для дизеля/газа ещё нет | T, G (паровая турбина) |
| Аккумуляторный буфер 1x1/2x2, батарейка LV | `BasicBatteryBuffer`, `Battery_RE_LV_Sodium` | `BatteryBufferBlockEntity`, `lv.item.BatteryItem` | перенесено (рецепт батарейки — отличие) | G |
| Трансформатор LV | `GT_MetaTileEntity_Transformer` | `TransformerBlockEntity` | перенесено (переключение ключом — отличие) | G |
| Детали LV, схема, корпус | `GT_MetaGenerated_Item_01`, `ProcessingCircuit` | `tools/gen/lv.py` | мотор, поршень, насос, конвейер, манипулятор, схема, резистор, вакуумная трубка, плата | S |
| Новые карты рецептов (8) | `GT_Recipe_Map` | `GtRecipeMap`, `tools/gen/lv.py` | волочильный, гибочный, токарный, сборщик, смеситель, поляризатор, рудопромывка, коксовая печь (~350 рецептов) | S, G |
| Каучук | IC2 (закрытый) → своя механика | `RubberLogBlock`, `TreetapItem`, worldgen | дерево, смола, краник, слизь | G (рост дерева) |
| Коксовая печь | Railcraft + `RailCraft.zs` | `CokeOvenBlockEntity` | перенесено (числа Railcraft по памяти) | G, C |
| Жидкость «креозот» | Railcraft / `Materials.Creosote` | `IISFluids.CREOSOTE` | с блоком и ведром | G |
| Электрическая доменная печь | `GT_MetaTileEntity_ElectricBlastFurnace` | `multi.block.EbfBlockEntity`, `multi.logic.BlastLogic` | перенесено (константановые катушки) | T, G |
| Люки LV, шины, обслуживание, выхлоп | `GT_MetaTileEntity_Hatch_*` | `multi.block.*` | перенесено | G |
| Техобслуживание | `MultiBlockBase`, `Hatch_Maintenance` | `multi.logic.Maintenance` | перенесено (инструмент по люку) | T, G |
| Киянка, монтировка, паяльник LV | `GT_Tool_SoftHammer/Crowbar`, `SOLDERING_IRON_LV` | инструменты GT, `multi.SolderingIronItem` | перенесено | S |
| Остальные LV-машины (центрифуга, электролизёр, химреактор, резчик, …), провода 8x+ | `GT_Loader_MetaTileEntities` | — | следующие блоки LV (D42) | — |
