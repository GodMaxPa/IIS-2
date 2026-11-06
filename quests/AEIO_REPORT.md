# AE2 и Ender IO: что из ИИС 1 не перенеслось (D125)

Рецептов ИИС 1 записано: 225. Выключено рецептов модов: AE2 210, Ender IO 1097. Скрыто предметов: 898.

- нет в ИИС 2: <gregtech:gt.metaitem.03:32047>: 3
  - `AE2.zs: Assembler.addRecipe(StorageComponent16K, [RAMchip*32, FluixDust*32, SoCchip], mEuropuim * 36, 30, 30720)`
  - `AE2.zs: Assembler.addRecipe(StorageComponent64K, [AdvCircuit*3, StorageComponent16K*3, SoCchip], mEuropuim * 36, 30, 30720)`
  - `AE2.zs: Assembler.addRecipe(<appliedenergistics2:item.ItemMultiMaterial:33>, [<ore:plateEnderPearl>*3, <appliedenergistics2:item.ItemMulti)`
- непонятное выражение в скрипте: 2
  - `AE2.zs: recipes.remove(slab)`
  - `AE2.zs: recipes.addShaped(slab * 2, [[<ore:craftingToolSaw>, blocks[i]]])`
- нет в новом Ender IO: <EnderIO:blockElectricLight>: 2
  - `EnderIO.zs: recipes.addShaped(<EnderIO:blockElectricLight>, [[null, null, null], [null, fusedQuartz, null], [<ore:plateIron>, <ore:springSma)`
  - `EnderIO.zs: recipes.addShaped(<EnderIO:blockElectricLight> * 2, [[null, null, null], [null, fusedQuartz, null], [<ore:plateIron>, <ore:sprin)`
- нет в новом AE2: <appliedenergistics2:tile.BlockSecurity>: 1
  - `AE2.zs: recipes.addShaped(<appliedenergistics2:tile.BlockSecurity>, [ [AlPlate, <appliedenergistics2:tile.BlockChest>, AlPlate], [FluixG)`
- нет в новом AE2: <appliedenergistics2:item.ToolBiometricCard>: 1
  - `AE2.zs: recipes.addShaped(<appliedenergistics2:item.ToolBiometricCard>, [ [DiamondCircuit, AlPlate, AlPlate], [<ore:plateGold>, <ore:pla)`
- нет в ИИС 2: <gregtech:gt.metaitem.03:32049>: 1
  - `AE2.zs: Assembler.addRecipe(<appliedenergistics2:item.ItemMultiMaterial:34>, [<ore:plateEnderEye>*3, <appliedenergistics2:item.ItemMultiMa)`
- мода нет в ИИС 2: <IC2:blockITNT>: 1
  - `AE2.zs: Centrifuge.addRecipe([TinyTNT, TinyTNT], ITNT, 0, 600)`
- нет в ИИС 2: <gregtech:gt.metaitem.01:24515>: 1
  - `AE2.zs: PrecisionLaser.addRecipe(pressSilicon, lensDilithium * 0, DenseSteel, 6000, 480)`
- нет в новом AE2: <appliedenergistics2:item.ItemMultiMaterial:54>: 1
  - `AE2.zs: recipes.addShaped(PatternCapacityCard, [[CardAdvanced, StorageComponent1K], [GoldCircuit, CapacityCard]])`
- нет в новом AE2: <appliedenergistics2:item.ItemMultiMaterial:55>: 1
  - `AE2.zs: recipes.addShaped(OreDictionaryFilterCard, [[CardAdvanced, FuzzyCard], [CapacityCard, GoldCircuit]])`
- нет в новом AE2: <appliedenergistics2:item.ItemMultiPart:500>: 1
  - `AE2.zs: recipes.addShapeless(ProcPatternTerminal, [PatternTerminal, CapacityCard, PatternCapacityCard])`
- нет в новом Ender IO: <EnderIO:blockBuffer>: 1
  - `EnderIO.zs: recipes.addShaped(<EnderIO:blockBuffer>, [ [<ore:plateIron>, <ore:plateSteel>, <ore:plateIron>], [<ore:plateSteel>, <minecraft:c)`
- нет в новом Ender IO: <EnderIO:itemRedstoneConduit:1>: 1
  - `EnderIO.zs: Assembler.addRecipe(<EnderIO:itemRedstoneConduit:1>, <EnderIO:itemRedstoneConduit:2>, <minecraft:lever>, 50, 60)`
- нет в новом Ender IO: <EnderIO:itemMEConduit>: 1
  - `EnderIO.zs: Assembler.addRecipe(<EnderIO:itemMEConduit>, <appliedenergistics2:item.ItemMultiPart:16>, <EnderIO:itemMaterial:1> * 4, 50, 120)`
- нет в новом Ender IO: <EnderIO:itemMEConduit:1>: 1
  - `EnderIO.zs: Assembler.addRecipe(<EnderIO:itemMEConduit:1>, <appliedenergistics2:item.ItemMultiPart:76>, <EnderIO:itemMaterial:1> * 16, 100, 48)`
- нет в новом Ender IO: <EnderIO:blockElectricLight:2>: 1
  - `EnderIO.zs: recipes.addShaped(<EnderIO:blockElectricLight:2>, [[null, null, null], [null, fusedQuartz, null], [<ore:plateIron>, <ore:glowsto)`
- нет в новом Ender IO: <EnderIO:blockCapBank:1>: 1
  - `EnderIO.zs: recipes.addShaped(<EnderIO:blockCapBank:1>, [ [<ore:wireGt02Iron>, <EnderIO:itemBasicCapacitor>, <ore:wireGt02Iron>], [<EnderIO:)`
- нет в новом Ender IO: <EnderIO:itemFunctionUpgrade>: 1
  - `EnderIO.zs: Assembler.addRecipe(<EnderIO:itemFunctionUpgrade>, [<ore:plateConductiveIron>, <ore:plateEnderEye>, <ore:circuitBasic>], null, 100)`
- нет в новом Ender IO: <EnderIO:itemExtractSpeedUpgrade>: 1
  - `EnderIO.zs: Assembler.addRecipe(<EnderIO:itemExtractSpeedUpgrade>, [<ore:plateElectricalSteel>, <ore:plateAluminium>, <ore:circuitBasic>], nul)`
- нет в новом Ender IO: <EnderIO:itemExtractSpeedUpgrade:1>: 1
  - `EnderIO.zs: Assembler.addRecipe(<EnderIO:itemExtractSpeedUpgrade:1>, [<ore:plateElectricalSteel>, <ore:plateRedAlloy>, <ore:circuitBasic>], nu)`

## Выключенные рецепты по причинам

- ae2: recipes.remove в AE2.zs: 106
- ae2: выключено в настройках ИИС 1 или нет в ИИС 1: 24
- ae2: нельзя получить ae2:cell_component_256k: 20
- ae2: пресс и зарядник скрыты в AE2.zs, путь идёт через машины GT: 18
- ae2: нельзя получить ae2:smooth_sky_stone_block: 10
- ae2: нельзя получить ae2:sky_stone_block: 9
- ae2: нельзя получить ae2:sky_stone_brick: 7
- ae2: в ИИС 1 нет выращивания кварца в мире: рецепты GT: 6
- ae2: нельзя получить ae2:sky_stone_small_brick: 6
- ae2: кремний делает GT: 2
- ae2: нельзя получить ae2:sky_dust: 1
- ae2: нельзя получить ae2:256k_crafting_storage: 1
- enderio: в ИИС 1 этого нет или оно скрыто: 800
- enderio: машины и обработка этого рода скрыты в EnderIO.zs: 206
- enderio: recipes.remove в EnderIO.zs: 86
- enderio: нельзя получить enderio:soularium_grinding_ball: 2
- enderio: нельзя получить enderio:soularium_pressure_plate: 1
- enderio: нельзя получить enderio:enderman_head: 1
- enderio: нельзя получить enderio:ender_resonator: 1
