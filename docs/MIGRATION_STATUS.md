# Статус переноса (матрица адаптации)

Эталон: GT5-II `IdealIndustrial/GT5Unofficial` @ `00021e3` + конфиги и скрипты сборки ИИС 1.19.1 (см. AGENTS.md).
Уровни доказательств: **T** — unit-тесты, **G** — GameTest (машины в мире), **S** — `/iis selftest` на сервере,
**C** — скриншот клиента в облаке, **O** — проверено владельцем в игре, **—** — не проверено.
Обновлено 2026-10-26 (после покрытий, D80). Био и Дуо — не перенос, а новые пути: см. `docs/BIO_PATH.md`.

| Подсистема | Оригинал | Наш код | Статус | Доказательства |
|---|---|---|---|---|
| Уровни напряжения, разгон | `GT_Values.V`, `BasicMachine#calculateOverclockedNess` | `energy.VoltageTier`, `machine.Overclock` | перенесено | T, G |
| Материалы и формы | `Materials`, `OrePrefixes`, `MaterialProperties.cfg` | `core.material`, `tools/gen/gen_content.py` | 137 материалов (с хромом и нержавейкой, D76); формы пыли, слитки, пластины, фольга, шестерни, тонкая проволока, кольца, роторы, огранки камней; нет ingotDouble+, plateDouble+, spring, crushedCentrifuged, ingotHot | T, S, C |
| Жилы и руды | `GT_Worldgen_GT_Ore_Layer`, `WorldGeneration.cfg` | `world.vein`, `world.ore` | перенесено (без космоса) | T, S, C, O (загрузка) |
| Малые руды | `GT_Worldgen_GT_Ore_SmallPieces` | `VeinFeature`, `OreBlocks` | перенесено (без удачи) | S |
| Инструменты GT | `GT_Tool_*`, `GT_MetaGenerated_Tool_01` | `tool.*` | меч, кирка, лопата, топор, мотыга, пила, молот, киянка, ключ, напильник, монтировка, отвёртка, ступка, кусачки, нож; электроинструменты LV — отдельной строкой; нет остальных инструментов GT5 | S, C, O (ключ) |
| Электроинструменты LV | `GT_MetaGenerated_Tool_01` (DRILL_LV, CHAINSAW_LV, WRENCH_LV), `GT_Tool_*_LV`, `ProcessingToolHead` | `lv.item.ElectricToolItem`, `lv.ElectricToolLogic` | бур, цепная пила, электроключ из 43 материалов; наконечники; заряд от батареи сборки, EU вместо прочности, износ 1/25, зарядка в буфере (D78) | T, G |
| Ручная переработка | `ProcessingOre/Ingot/...`, котёл | рецепты + `MaterialItem` | перенесено | S |
| Пар: жидкость | `GT_ModHandler.getSteam` | `fluid.IISFluids` | перенесено | G |
| Малые котлы (4) | `GT_MetaTileEntity_Boiler_*` | `machine.logic.BoilerLogic`, `BoilerBlockEntity` | перенесено | T, G, C |
| Паровые машины (6×2) | `machines/steam/*`, `BasicMachine_Bronze` | `SteamMachineLogic`, `SteamMachineBlockEntity` | перенесено (выход всегда сзади) | T, G, C |
| Карты рецептов GT | `GT_Recipe_Map` | `machine.recipe.*`, `tools/gen/*.py` | все карты пара, LV, LV II, LV III, LV IV и доменных печей; всего в игре 7 790 рецептов (D80) | S, G |
| Жидкостные трубы | `GT_MetaPipeEntity_Fluid`, `generateFluidPipes` | `PipeBlock`, `PipeBlockEntity` | 43 исполнения: дерево (3 размера) и 8 материалов × 5 размеров; опасности GT (огонь, утечка газа, ожог), D72. Крафт — только где есть пластина (дерево, медь, бронза, сталь, нержавейка) | G, C |
| Кирпичная доменная печь | `GT_MetaTileEntity_PrimitiveBlastFurnace` | `BbfStructure`, `BbfBlockEntity` | перенесено | T, G, C |
| Огнеупорная цепочка | `GT_CraftingRecipeLoader`, `GT_MachineRecipeLoader` | `tools/gen/steam.py` | перенесено | S |
| Просмотр рецептов | NEI | JEI-плагины `machine.compat.jei`, `bio.compat.jei` | категории всех машин с жидкостями, печи как станции плавки, программная схема над стрелкой | C |
| Загрязнение | `GT_Pollution` | `ru.iis.pollution` | перенесено (D53) | T, G (котёл) |
| EU: запас, приём, выдача, взрывы | `BaseMetaTileEntity` | `energy.logic.EnergyStorage`, `lv.block.ElectricBlockEntity` | перенесено (взрыв в конце тика, D48) | T, G |
| Провода и кабели | `GT_MetaPipeEntity_Cable`, `makeWires` | `energy.logic.EnergyNet`, `WorldEnergy`, `lv.block.CableBlock` | 17 материалов `makeWires`, которые есть в ИИС 2: провода 1x–16x, кабели 1x–12x; изоляция бумагой, ковром или расплавом резины; сборка жил в сборщике со схемой (D79); нет титана, вольфрама и других материалов старших эпох | T, G, C |
| Опасности (дождь, вода, огонь, гроза, удар током, сгорание, wirefire) | `BaseMetaTileEntity#updateEntity`, `GT_Utility#applyElectricityDamage` | `ElectricBlockEntity`, `CableBlock` | перенесено; переключатели в serverconfig (D47) | G (вода, перенапряжение, сгорание, удар) |
| LV-машины (25) | `BasicMachine_GT_Recipe`, `GT_Loader_MetaTileEntities` | `machine.logic.ElectricMachineLogic`, `lv.block.ElectricMachineBlockEntity`, `LvMachineType` | дробитель, печь, печь сплавов, компрессор, экстрактор, молот, волочильный, гибочный, токарный, сборщик, смеситель, поляризатор, рудопромывка; LV II — центрифуга, просеиватель, электромагнитный сепаратор, автоклав, пильный, консервирующий; LV III — электролизёр, химреактор, экстрактор и отвердитель жидкостей; LV IV — дистиллятор, химическая ванна (D77) | T, G, C |
| Выходной бак жидкости LV-машин | `BasicMachine#mOutputFluid` | `ElectricMachineBlockEntity` | перенесено, автовыгрузка жидкости кнопкой (D76) | G |
| Кнопки автовыгрузки | `GT_MetaTileEntity_BasicMachine` (`mItemTransfer`/`mFluidTransfer`) | `ElectricMachineBlockEntity` | перенесено (D57) | G |
| Генераторы LV (3) | `BasicGenerator`, `SteamTurbine`, `DieselGenerator`, `GasTurbine` | `energy.logic.GeneratorLogic`, `lv.block.GeneratorBlockEntity`, `lv.Fuels` | перенесено; топливо — таблица GT (`iis/fuels.json`), из неё зарегистрированы 6 дизельных и 9 газовых жидкостей GT, на LV добывается только водород (электролиз воды) | T, G (паровая турбина) |
| Аккумуляторный буфер 1x1/2x2, батарейки LV | `BasicBatteryBuffer`, `GT_MetaGenerated_Item_01:401-465` | `BatteryBufferBlockEntity`, `lv.item.BatteryItem` | перенесено: корпус + консервирующий станок, натриевая/кадмиевая/литиевая (D63) | G |
| Трансформатор LV | `GT_MetaTileEntity_Transformer` | `TransformerBlockEntity` | перенесено (режим — Shift+ключ, отличие) | G |
| Детали LV, схема, корпус | `GT_MetaGenerated_Item_01`, `ProcessingCircuit` | `tools/gen/lv.py` | мотор, поршень, насос, конвейер, манипулятор, схема, резистор, вакуумная трубка, плата | S |
| Программная схема | `GT_IntegratedCircuit_Item` | `lv.item.ProgrammedCircuitItem`, компонент `iis:circuit_config`, поле `circuit` рецепта | конфигурация 0…24, отвёртка, не расходуется (D75) | G |
| Каучук | IC2 (закрытый) → своя механика | `RubberLogBlock`, `TreetapItem`, worldgen | дерево, смола, краник, слизь | G (рост дерева) |
| Коксовая печь | Railcraft + `RailCraft.zs` | `CokeOvenBlockEntity` | перенесено (числа Railcraft по памяти) | G, C, O |
| Жидкость «креозот» | Railcraft / `Materials.Creosote` | `IISFluids.CREOSOTE` | с блоком и ведром | G, O |
| Жидкости GT | `GT_Loader_Item_Block_And_Fluid`, `GT_Proxy#addFluid` | `fluid.GtFluids`, `iis/gt_fluids.json` | 470: 465 из языкового файла ИИС, 4 кислоты GT5U (соляная, разбавленная соляная, плавиковая, фосфорная) и дистиллированная вода вместо IC2; без блока и ведра (D72, D77) | G |
| Капсулы | `Cell_Empty`, IC2 `Cell_Universal_Fluid`, стальные капсулы GT | `fluid.CapsuleItem` | обычная, универсальная (рецепт — отличие), стальная и из магнитной стали; ПТФЭ — позже | G |
| Резервуары | — (своё по просьбе владельца) | `fluid.UniversalTankBlock` | бронзовый 32 000 и стальной 128 000 мБ, одноблочные; ёмкости временные | G |
| Электрическая доменная печь | `GT_MetaTileEntity_ElectricBlastFurnace` | `multi.block.EbfBlockEntity`, `multi.logic.BlastLogic` | перенесено (константановые катушки, хром и нержавейка 1700 K) | T, G |
| Люки LV, шины, обслуживание, выхлоп | `GT_MetaTileEntity_Hatch_*` | `multi.block.*` | перенесено | G |
| Техобслуживание | `MultiBlockBase`, `Hatch_Maintenance` | `multi.logic.Maintenance` | перенесено (инструмент по люку) | T, G |
| Киянка, монтировка, паяльник LV | `GT_Tool_SoftHammer/Crowbar`, `SOLDERING_IRON_LV` | инструменты GT, `multi.SolderingIronItem` | перенесено | S |
| Алмазный пильный диск, фильтр, кобальтовая латунь, огранки камней (осколки…изысканный) | `GT_MetaGenerated_Item_01/02`, `OrePrefixes` | `tools/gen` | перенесено | S |
| Покрытия | `GT_Cover_*` | `ru.iis.cover` | 13 видов, с регулятором и фильтром жидкости (D80), на электроблоках, паровых машинах, трубах, шинах и люках жидкости и энергии; нет солнечных панелей, экрана, беспроводного сигнала | T, G, C |
| Химия LV III | `ProcessingDust:61–110`, `registerReverseFluidSmelting`, `GT_MachineRecipeLoader` | `tools/gen/extract_gt5_recipes.py`, `tools/gen/chem.py` | электролиз и центрифуга пылей с капсулами, плавка и литьё с формами, 311 рецептов химреактора; рецепт берётся, только если все его предметы и жидкости есть — из 869 вызовов пропущено 440 (D76, D77) | G |
| Дистилляция и химванна (LV IV) | `GT_RecipeAdder#addDistilleryRecipe`, `#addChemicalBathRecipe`, `ProcessingDirty:26–35` | `tools/gen/chem.py` | 20 рецептов дистиллятора (вода → дистиллированная, нефть и топливо), 93 химванны: ртуть и персульфат натрия для руд, хлор для шерсти, ковров, стекла и терракоты, бумага; варианты пильного станка и автоклава на дистиллированной воде и смазке (D77) | G |
| Формы отвердителя | `ItemList.Shape_Mold_*` | `tools/gen` | слиток, пластина, самородки, шестерня, блок; форма не расходуется (`keep`) | G |
| Мультиметр | Portable Scanner (HV) | `lv.item.MultimeterItem`, `lv.EnergyReadout` | с LV и с живым режимом (отличие, D51) | G |
| Геологоразведка | GT_Scanner_Mod | `compat.journeymap`, `/iis scan`, `/iis vein` | слой JourneyMap «сетка рудных чанков» (D60); прибора-сканера нет | T, C |
| Валка деревьев | Treecapitator (отдельный мод) | `world.TreeFeller` | топор GT валит дерево целиком (D56) | G |
| Детали MV, корпус и оболочка MV, хорошая схема, диод | `GT_MetaGenerated_Item_01:483–711`, `GT_MachineRecipeLoader:475–537` | `lv.LvContent`, `tools/gen/lv.py` | перенесено (диод — из галлия; кремниевая пластина — позже) | S |
| Термоцентрифуга LV/MV, центрифугированная руда | `GT_Loader_MetaTileEntities:784–785`, `ProcessingDirty:24`, `ProcessingCrushedOre:34–41` | `lv.LvMachineType` (уровень машины), `tools/gen/lv.py` | перенесено; LV не тянет рецепты 48 EU/t (как в GT) | T, G |
| Горячие слитки | `OrePrefixes.ingotHot`, `ProcessingDust:44`, `GT_Proxy:1363` | `multi.HotIngotHeat`, `tools/gen/ebf.py` | иридий и палладий из ЭДП, урон жаром; до катушек выше 1800 K недостижимы | T, G |
| Вакуумный морозильник | `GT_MetaTileEntity_VacuumFreezer` | `multi.block.VacuumFreezerBlockEntity`, `multi.logic.FreezerLogic` | перенесено; крафт контроллера — с помпой HV и схемой «Данные» | T, G |
| Большие котлы | `GT_MetaTileEntity_LargeBoiler_*` | `multi.block.LargeBoilerBlockEntity`, `multi.logic.LargeBoilerLogic` | бронзовый, стальной (D92), титановый (D97) и вольфрамостальной (D98) крафтятся | T, G |
| Большая паровая турбина, динамо-люки, роторы | `GT_MetaTileEntity_LargeTurbine_Steam`, `GT_MetaTileEntity_Hatch_Dynamo`, `GT_Tool_Turbine_*` | `multi.block.LargeTurbineBlockEntity`, `multi.block.DynamoHatchBlockEntity`, `multi.TurbineRotorItem`, `multi.logic.LargeTurbineLogic` | перенесено; динамо-люки, турбина (D92), корпус и малый ротор (D90), обычный (D97) и большой (D98, сборщик EV) роторы крафтятся; огромный — с америцием | T, G |
| MV-версии 25 базовых машин, энергетический люк MV, трансформатор MV | `GT_Loader_MetaTileEntities` Machine_MV_*, `:196, 238` | `lv.LvMachineType` (`*_MV`), `multi.block.HatchType.ENERGY_MV`, `lv.block.TransformerBlock#low`, `tools/gen/lv.py` (`MV_CRAFT`) | перенесено (D82) | T, G |
| Экструдер LV/MV, 23 насадки, формы печи сплавов (оболочка, бутылка, наковальня) | `ProcessingShaping`, `GT_Loader_MetaTileEntities:685–686` | `lv.LvMachineType.EXTRUDER*`, `tools/gen/mv.py` | перенесено (D83) | T, G |
| Формовочный пресс, нагреватель жидкостей, электродуховка LV/MV | `GT_Loader_MetaTileEntities:1039–1102` | `lv.LvMachineType`, `tools/gen/mv.py` | перенесено (D84) | T, G |
| Дуговая печь LV/MV, переработка форм | `GT_RecipeRegistrator#registerReverseArcSmelting`, `GT_Loader_MetaTileEntities:1084–1085` | `lv.LvMachineType.ARC_FURNACE*`, `tools/gen/mv.py` | перенесено (D85); переработка машин и кабелей — нет | T, G |
| Лазерный гравёр LV/MV, излучатели LV/MV, линзы | `GT_Loader_MetaTileEntities:1030`, `ProcessingLens`, `ProcessingCrafting` | `lv.LvMachineType.LASER_ENGRAVER*`, `tools/gen/mv.py` | перенесено (D86); микросхемы — с кремниевыми пластинами | T, G |
| Остальные LV- и MV-машины GT5 (микроволновка, принтер, ферментер, консервер жидкостей и др.), генераторы, буферы, шины и люки MV, электроотвёртка LV | `GT_Loader_MetaTileEntities` | — | дальше по плану MV | — |
| Пивоварня и сборщик схем LV/MV, биомасса, пластик, клей, схема HV, корпус HV | `GT_MetaTileEntity_PotionBrewer`, `GT_Loader_MetaTileEntities:174, 931, 1374`, `GT_MachineRecipeLoader:442–563, 767, 1404–1407`, `GT_RecipeAdder:271–276` | `lv.LvMachineType.BREWERY*`, `CIRCUIT_ASSEMBLER*`, `tools/gen/large.py` (`d92`) | перенесено (D92); зелья пивоварни — нет | T, G |
| HV-версии базовых машин, детали HV, люки HV, кантал и катушка ЭДП 2700 K | `Machine_HV_*`, `Tier.ELECTRIC[3]`, `GT_MetaGenerated_Item_01:496–732`, `GT_Loader_MetaTileEntities:92, 217, 239, 1482` | `lv.LvMachineType.*_HV`, `multi.block.HatchType.ENERGY_HV/DYNAMO_HV`, `EbfBlockEntity#coilHeat`, `tools/gen/lv.py` (`HV_MACHINES`, `XHV`) | перенесено (D93); сборщик схем HV — без крафта до схемы Data | T, G |
| Генераторы HV (паровая, дизельный, газовая), буферы HV 1×1–4×4, трансформатор EV↔HV, малая катушка | `GT_Loader_MetaTileEntities:197, 488–567, 1163–1187` | `lv.GeneratorType.*_HV`, `LvContent.BATTERY_BUFFERS_HV`, `TRANSFORMER_HV` | перенесено (D94); генераторы и буферы MV, буферы LV 3×3/4×4 — D95 | T, G |
| Батарейки и корпуса MV/HV (натриевые, кадмиевые, литиевые) | `GT_MetaGenerated_Item_01:401–471`, `GT_MachineRecipeLoader:1428–1431` | `lv.LvContent.BATTERY_*_MV/HV`, `tools/gen/lv.py` | перенесено (D96); одноразовые батарейки — нет | T, G |
| Вольфрам, вольфрамосталь, нихром и катушка 3600 K, корпус и детали EV, сборщик и химреактор EV, схема Elite | `GT_MachineRecipeLoader2:36–52`, `GT_MachineRecipeLoader:572, 1029, 1033`, `Machine_EV_*` | `lv.LvMachineType.ASSEMBLER_EV/CHEMICAL_REACTOR_EV`, `EbfBlockEntity#coilHeat`, `tools/gen/large.py` | перенесено (D98); остальная эпоха EV — нет | T, G |
