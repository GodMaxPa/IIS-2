package ru.iis.multi;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.lv.item.BatteryItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.block.*;
import ru.iis.core.material.MaterialData;
import ru.iis.multi.logic.LargeBoilerLogic;
import ru.iis.multi.logic.LargeTurbineLogic;
import ru.iis.multi.menu.ControllerMenu;
import ru.iis.multi.menu.EbfMenu;
import ru.iis.multi.menu.FluidHatchMenu;
import ru.iis.multi.menu.ItemBusMenu;

import java.util.*;
import java.util.function.Supplier;

/**
 * Многоблочные машины: электрическая доменная печь, вакуумный морозильник, большие котлы и паровая турбина, люки,
 * корпуса, катушки, каркасы, роторы, паяльник. Модели, переводы и рецепты — tools/gen/ebf.py и tools/gen/large.py
 * (котлы D88, турбина D89).
 */
public final class MultiContent {
    private MultiContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IIS.MODID);

    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();

    private static Block.Properties metal(Block.Properties p) {
        return p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, java.util.function.Function<Block.Properties, B> f, java.util.function.UnaryOperator<Block.Properties> props) {
        DeferredBlock<B> b = BLOCKS.registerBlock(id, f, props);
        TAB_ORDER.add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    public static final List<String> FRAME_MATERIALS = List.of("invar", "steel", "iron", "bronze", "aluminium", "blue_steel", "titanium", "tungsten_steel", "stainless_steel", "polytetrafluoroethylene",
            "black_steel");
    public static final Map<String, DeferredBlock<Block>> FRAMES = new LinkedHashMap<>();
    static {
        for (String m : FRAME_MATERIALS) {
            FRAMES.put(m, block("frame_" + m, Block::new, p -> metal(p).noOcclusion().strength(3.0f, 6.0f)));
        }
    }
    public static final DeferredBlock<Block> HEAT_PROOF_CASING = block("heat_proof_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> COIL_CUPRONICKEL = block("coil_cupronickel", Block::new, MultiContent::metal);
    /** GT_Block_Casings5 meta 1 (D93): 2700 K. */
    public static final DeferredBlock<Block> COIL_KANTHAL = block("coil_kanthal", Block::new, MultiContent::metal);
    /** GT_Block_Casings5 meta 2 (D98): 3600 K. */
    public static final DeferredBlock<Block> COIL_NICHROME = block("coil_nichrome", Block::new, MultiContent::metal);
    /** GT_Block_Casings5 meta 3 (D103): 4500 K — ванадий-галлий. */
    public static final DeferredBlock<Block> COIL_TUNGSTENSTEEL = block("coil_tungstensteel", Block::new, MultiContent::metal);
    /** GT_Block_Casings2 meta 1: морозостойкий корпус вакуумного морозильника (D81). */
    public static final DeferredBlock<Block> FROST_PROOF_CASING = block("frost_proof_casing", Block::new, MultiContent::metal);
    public static final Map<HatchType, DeferredBlock<HatchBlock>> HATCHES = new EnumMap<>(HatchType.class);
    static {
        for (HatchType t : HatchType.values()) HATCHES.put(t, block(t.id, p -> new HatchBlock(t, p), MultiContent::metal));
    }
    public static final DeferredBlock<EbfBlock> EBF = block("electric_blast_furnace", EbfBlock::new,
            p -> metal(p).lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 13 : 0));
    public static final DeferredBlock<EbfBlock> VACUUM_FREEZER = block("vacuum_freezer", p -> new EbfBlock(p, VacuumFreezerBlockEntity::new), MultiContent::metal);
    // D104: многоблоки по картам рецептов GT
    public static final DeferredBlock<EbfBlock> IMPLOSION_COMPRESSOR = block("implosion_compressor", p -> new EbfBlock(p, ImplosionCompressorBlockEntity::new), MultiContent::metal);
    public static final DeferredBlock<EbfBlock> MULTI_SMELTER = block("multi_smelter", p -> new EbfBlock(p, MultiSmelterBlockEntity::new), MultiContent::metal);
    public static final DeferredBlock<EbfBlock> PYROLYSE_OVEN = block("pyrolyse_oven", p -> new EbfBlock(p, PyrolyseOvenBlockEntity::new), MultiContent::metal);
    public static final DeferredBlock<EbfBlock> DISTILLATION_TOWER = block("distillation_tower", p -> new EbfBlock(p, DistillationTowerBlockEntity::new), MultiContent::metal);
    public static final DeferredBlock<EbfBlock> OIL_CRACKER = block("oil_cracker", p -> new EbfBlock(p, OilCrackerBlockEntity::new), MultiContent::metal);
    public static final DeferredBlock<EbfBlock> LARGE_CHEMICAL_REACTOR = block("large_chemical_reactor", p -> new EbfBlock(p, LargeChemicalReactorBlockEntity::new), MultiContent::metal);
    /** GT_Block_Casings8 meta 0 и 1 (D104): химически стойкий корпус и корпус с ПТФЭ-трубами — большой химреактор. */
    public static final DeferredBlock<Block> CHEMICALLY_INERT_CASING = block("chemically_inert_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> PTFE_PIPE_CASING = block("ptfe_pipe_casing", Block::new, MultiContent::metal);
    /** GT_Block_Casings4 meta 1 (D104): чистый корпус из нержавеющей стали — колонна и крекер. */
    public static final DeferredBlock<Block> CLEAN_STAINLESS_CASING = block("clean_stainless_casing", Block::new, MultiContent::metal);
    /** GT_Block_Casings1 meta 0 (D104): каркас механизма ULV — пиролизная печь. */
    public static final DeferredBlock<Block> CASING_ULV = block("casing_ulv", Block::new, MultiContent::metal);

    /** Корпуса больших котлов (D88): GT_Block_Casings1:10, Casings2:0, 12, 13, Casings3:13, 14. */
    public static final DeferredBlock<Block> BRONZE_PLATED_BRICKS = block("bronze_plated_bricks", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> BRONZE_PIPE_CASING = block("bronze_pipe_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> BRONZE_FIREBOX_CASING = block("bronze_firebox_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> SOLID_STEEL_CASING = block("solid_steel_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> STEEL_PIPE_CASING = block("steel_pipe_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> STEEL_FIREBOX_CASING = block("steel_firebox_casing", Block::new, MultiContent::metal);
    /** D102: GT_Block_Casings2:3 стальной корпус коробки передач (локомотивы и бур Railcraft по скрипту ИИС 1). */
    public static final DeferredBlock<Block> STEEL_GEAR_BOX_CASING = block("steel_gear_box_casing", Block::new, MultiContent::metal);
    /** D97: GT_Block_Casings4:2 стабильный титановый корпус, Casings2:14 титановые трубы, Casings4:3 титановая топка. */
    public static final DeferredBlock<Block> STABLE_TITANIUM_CASING = block("stable_titanium_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> TITANIUM_PIPE_CASING = block("titanium_pipe_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> TITANIUM_FIREBOX_CASING = block("titanium_firebox_casing", Block::new, MultiContent::metal);
    /** D98: GT_Block_Casings4:0 прочный вольфрамостальной корпус, Casings2:15 трубы, Casings3:15 топка. */
    public static final DeferredBlock<Block> ROBUST_TUNGSTENSTEEL_CASING = block("robust_tungstensteel_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> TUNGSTENSTEEL_PIPE_CASING = block("tungstensteel_pipe_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> TUNGSTENSTEEL_FIREBOX_CASING = block("tungstensteel_firebox_casing", Block::new, MultiContent::metal);
    /** Большие котлы GT (GT_Loader_MetaTileEntities:1152–1155). */
    public static final Map<LargeBoilerLogic.Tier, DeferredBlock<LargeBoilerBlock>> LARGE_BOILERS = new EnumMap<>(LargeBoilerLogic.Tier.class);
    static {
        for (LargeBoilerLogic.Tier t : LargeBoilerLogic.Tier.values()) {
            LARGE_BOILERS.put(t, block("large_boiler_" + t.name().toLowerCase(Locale.ROOT), p -> new LargeBoilerBlock(p, t),
                    p -> metal(p).lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 13 : 0)));
        }
    }

    /** GT_Block_Casings4:9 — турбинный корпус (D89); большая паровая турбина GT_Loader_MetaTileEntities:1243. */
    public static final DeferredBlock<Block> TURBINE_CASING = block("turbine_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<EbfBlock> LARGE_STEAM_TURBINE = block("large_steam_turbine", p -> new EbfBlock(p, LargeTurbineBlockEntity::new), MultiContent::metal);
    /** D104: GT_MetaTileEntity_LargeTurbine_Gas и GT_Block_Casings4 meta 10 (турбинный корпус из нержавеющей стали). */
    public static final DeferredBlock<EbfBlock> LARGE_GAS_TURBINE = block("large_gas_turbine", p -> new EbfBlock(p, LargeGasTurbineBlockEntity::new), MultiContent::metal);
    public static final DeferredBlock<Block> TURBINE_CASING_STAINLESS = block("turbine_casing_stainless", Block::new, MultiContent::metal);
    /** D105: GT_MetaTileEntity_LargeTurbine_HPSteam и GT_Block_Casings4 meta 11 (титановый турбинный корпус). */
    public static final DeferredBlock<EbfBlock> LARGE_HP_TURBINE = block("large_hp_steam_turbine", p -> new EbfBlock(p, LargeHpTurbineBlockEntity::new), MultiContent::metal);
    public static final DeferredBlock<Block> TURBINE_CASING_TITANIUM = block("turbine_casing_titanium", Block::new, MultiContent::metal);
    /** D105: GT_MetaTileEntity_HeatExchanger. */
    public static final DeferredBlock<EbfBlock> HEAT_EXCHANGER = block("heat_exchanger", p -> new EbfBlock(p, HeatExchangerBlockEntity::new), MultiContent::metal);
    /** D105: GT_MetaTileEntity_ProcessingArray (на прочных вольфрамостальных корпусах). */
    public static final DeferredBlock<EbfBlock> PROCESSING_ARRAY = block("processing_array", p -> new EbfBlock(p, ProcessingArrayBlockEntity::new), MultiContent::metal);
    /** D105: GT_MetaTileEntity_Charcoal_Pit и GT_Block_Reinforced meta 4 (ломкий уголь: 1–2 древесных угля, топор). */
    public static final DeferredBlock<EbfBlock> CHARCOAL_PILE = block("charcoal_pile", p -> new EbfBlock(p, CharcoalPileBlockEntity::new), MultiContent::metal);
    public static final DeferredBlock<Block> BRITTLE_CHARCOAL = block("brittle_charcoal", Block::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).sound(SoundType.WOOD).strength(0.5f, 8.0f).requiresCorrectToolForDrops());
    /** D105: GT_MetaTileEntity_Cleanroom; GT_Block_Reinforced meta 2 (пласкрит), GT_Block_Glass_Casings meta 0 (окно), Casings3 meta 11 (фильтр). */
    public static final DeferredBlock<EbfBlock> CLEANROOM = block("cleanroom", p -> new EbfBlock(p, CleanroomBlockEntity::new), MultiContent::metal);
    public static final DeferredBlock<Block> PLASCRETE = block("plascrete", Block::new,
            p -> p.mapColor(MapColor.STONE).sound(SoundType.STONE).strength(40.0f, 100.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> PLASCRETE_WINDOW = block("plascrete_window",
            net.minecraft.world.level.block.TransparentBlock::new,
            p -> p.mapColor(MapColor.STONE).sound(SoundType.GLASS).strength(40.0f, 100.0f).requiresCorrectToolForDrops().noOcclusion()
                    .isValidSpawn((s, l, pos, e) -> false).isRedstoneConductor((s, l, pos) -> false).isSuffocating((s, l, pos) -> false)
                    .isViewBlocking((s, l, pos) -> false));
    public static final DeferredBlock<Block> FILTER_CASING = block("filter_casing", Block::new, MultiContent::metal);
    /** D106: шахтная труба и наконечник (вместо miningPipe / miningPipeTip IC2), буровые GT_MetaTileEntity_OilDrill1–3,
     *  OreDrillingPlant1–2, ConcreteBackfiller1–2; светлый бетон GT_Block_Concretes meta 8. */
    public static final DeferredBlock<MiningPipeBlock> MINING_PIPE = block("mining_pipe", MiningPipeBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(1.0f, 6.0f).noOcclusion());
    public static final DeferredBlock<MiningPipeBlock> MINING_PIPE_TIP = BLOCKS.registerBlock("mining_pipe_tip", MiningPipeBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(1.0f, 6.0f).noOcclusion());
    public static final DeferredBlock<Block> LIGHT_CONCRETE = block("light_concrete", Block::new,
            p -> p.mapColor(MapColor.STONE).sound(SoundType.STONE).strength(1.5f, 6.0f).requiresCorrectToolForDrops());
    public static final List<DeferredBlock<EbfBlock>> OIL_DRILLS = List.of(
            block("oil_drill_1", p -> new EbfBlock(p, OilDrillBlockEntity::new), MultiContent::metal),
            block("oil_drill_2", p -> new EbfBlock(p, OilDrillBlockEntity::new), MultiContent::metal),
            block("oil_drill_3", p -> new EbfBlock(p, OilDrillBlockEntity::new), MultiContent::metal));
    public static final List<DeferredBlock<EbfBlock>> ORE_DRILLS = List.of(
            block("ore_drill_1", p -> new EbfBlock(p, OreDrillBlockEntity::new), MultiContent::metal),
            block("ore_drill_2", p -> new EbfBlock(p, OreDrillBlockEntity::new), MultiContent::metal));
    public static final List<DeferredBlock<EbfBlock>> BACKFILLERS = List.of(
            block("concrete_backfiller_1", p -> new EbfBlock(p, BackfillerBlockEntity::new), MultiContent::metal),
            block("concrete_backfiller_2", p -> new EbfBlock(p, BackfillerBlockEntity::new), MultiContent::metal));

    /** D107: GT_Block_Casings8 meta 10–15 — заборники насосов ULV–IV; GT_MetaTileEntity_WaterPumpPrimitive / _Electric LV–IV. */
    public static final List<DeferredBlock<Block>> PUMP_INTAKES = java.util.stream.Stream.of("ulv", "lv", "mv", "hv", "ev", "iv")
            .map(t -> block("pump_intake_" + t, Block::new, MultiContent::metal)).toList();
    public static final List<DeferredBlock<EbfBlock>> WATER_PUMPS = java.util.stream.Stream.of("ulv", "lv", "mv", "hv", "ev", "iv")
            .map(t -> block("water_pump_" + t, p -> new EbfBlock(p, WaterPumpBlockEntity::new), MultiContent::metal)).toList();

    /** Номер блока контроллера в ряду уровней (I — 0); −1 — не из ряда. */
    public static int index(List<DeferredBlock<EbfBlock>> list, Block b) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).get() == b) return i;
        return -1;
    }

    private static Block[] blocks(List<DeferredBlock<EbfBlock>> list) {
        return list.stream().map(DeferredBlock::get).toArray(Block[]::new);
    }

    /** Материал ротора турбины (GT: основной материал инструмента). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ROTOR_MATERIAL = COMPONENTS.registerComponentType("rotor_material",
            b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** Роторы GT_MetaGenerated_Tool_01 170, 172, 174, 176: малый, обычный, большой, огромный. */
    public static final Map<LargeTurbineLogic.Size, DeferredItem<TurbineRotorItem>> ROTORS = new EnumMap<>(LargeTurbineLogic.Size.class);
    static {
        for (LargeTurbineLogic.Size s : LargeTurbineLogic.Size.values()) {
            ROTORS.put(s, ITEMS.registerItem("turbine_" + s.name().toLowerCase(Locale.ROOT), p -> new TurbineRotorItem(p, s), p -> p.stacksTo(1)));
        }
    }

    public static LargeBoilerBlockEntity.Casings boilerCasings(LargeBoilerLogic.Tier t) {
        return switch (t) {
            case STEEL -> new LargeBoilerBlockEntity.Casings(SOLID_STEEL_CASING.get(), STEEL_PIPE_CASING.get(), STEEL_FIREBOX_CASING.get());
            case TITANIUM -> new LargeBoilerBlockEntity.Casings(STABLE_TITANIUM_CASING.get(), TITANIUM_PIPE_CASING.get(), TITANIUM_FIREBOX_CASING.get());
            case TUNGSTENSTEEL -> new LargeBoilerBlockEntity.Casings(ROBUST_TUNGSTENSTEEL_CASING.get(), TUNGSTENSTEEL_PIPE_CASING.get(),
                    TUNGSTENSTEEL_FIREBOX_CASING.get());
            default -> new LargeBoilerBlockEntity.Casings(BRONZE_PLATED_BRICKS.get(), BRONZE_PIPE_CASING.get(), BRONZE_FIREBOX_CASING.get());
        };
    }

    public static final DeferredItem<SolderingIronItem> SOLDERING_IRON = ITEMS.registerItem("soldering_iron_lv", SolderingIronItem::new, p -> p.stacksTo(1));

    /** Блоки люков этих видов, со всеми уровнями (шины и люки MV–IV, D103). */
    private static Block[] hatches(HatchType... types) {
        java.util.Set<HatchType> kinds = java.util.Set.of(types);
        return Arrays.stream(HatchType.values()).filter(t -> kinds.contains(t) || kinds.contains(t.kind()) && t.kind() != t)
                .map(t -> HATCHES.get(t).get()).toArray(Block[]::new);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyHatchBlockEntity>> ENERGY_HATCH_BE = BLOCK_ENTITIES.register("energy_hatch",
            () -> new BlockEntityType<>(EnergyHatchBlockEntity::new, hatches(HatchType.ENERGY, HatchType.ENERGY_MV, HatchType.ENERGY_HV, HatchType.ENERGY_EV, HatchType.ENERGY_IV)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemBusBlockEntity>> ITEM_BUS_BE = BLOCK_ENTITIES.register("item_bus",
            () -> new BlockEntityType<>(ItemBusBlockEntity::new, hatches(HatchType.INPUT_BUS, HatchType.OUTPUT_BUS)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidHatchBlockEntity>> FLUID_HATCH_BE = BLOCK_ENTITIES.register("fluid_hatch",
            () -> new BlockEntityType<>(FluidHatchBlockEntity::new, hatches(HatchType.INPUT_HATCH, HatchType.OUTPUT_HATCH)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaintenanceHatchBlockEntity>> MAINTENANCE_HATCH_BE = BLOCK_ENTITIES.register("maintenance_hatch",
            () -> new BlockEntityType<>(MaintenanceHatchBlockEntity::new, hatches(HatchType.MAINTENANCE)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EbfBlockEntity>> EBF_BE = BLOCK_ENTITIES.register("electric_blast_furnace",
            () -> new BlockEntityType<>(EbfBlockEntity::new, EBF.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ImplosionCompressorBlockEntity>> IMPLOSION_COMPRESSOR_BE = BLOCK_ENTITIES.register("implosion_compressor",
            () -> new BlockEntityType<>(ImplosionCompressorBlockEntity::new, IMPLOSION_COMPRESSOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiSmelterBlockEntity>> MULTI_SMELTER_BE = BLOCK_ENTITIES.register("multi_smelter",
            () -> new BlockEntityType<>(MultiSmelterBlockEntity::new, MULTI_SMELTER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PyrolyseOvenBlockEntity>> PYROLYSE_OVEN_BE = BLOCK_ENTITIES.register("pyrolyse_oven",
            () -> new BlockEntityType<>(PyrolyseOvenBlockEntity::new, PYROLYSE_OVEN.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DistillationTowerBlockEntity>> DISTILLATION_TOWER_BE = BLOCK_ENTITIES.register("distillation_tower",
            () -> new BlockEntityType<>(DistillationTowerBlockEntity::new, DISTILLATION_TOWER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OilCrackerBlockEntity>> OIL_CRACKER_BE = BLOCK_ENTITIES.register("oil_cracker",
            () -> new BlockEntityType<>(OilCrackerBlockEntity::new, OIL_CRACKER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeChemicalReactorBlockEntity>> LARGE_CHEMICAL_REACTOR_BE = BLOCK_ENTITIES.register("large_chemical_reactor",
            () -> new BlockEntityType<>(LargeChemicalReactorBlockEntity::new, LARGE_CHEMICAL_REACTOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VacuumFreezerBlockEntity>> VACUUM_FREEZER_BE = BLOCK_ENTITIES.register("vacuum_freezer",
            () -> new BlockEntityType<>(VacuumFreezerBlockEntity::new, VACUUM_FREEZER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeBoilerBlockEntity>> LARGE_BOILER_BE = BLOCK_ENTITIES.register("large_boiler",
            () -> new BlockEntityType<>(LargeBoilerBlockEntity::new, LARGE_BOILERS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DynamoHatchBlockEntity>> DYNAMO_HATCH_BE = BLOCK_ENTITIES.register("dynamo_hatch",
            () -> new BlockEntityType<>(DynamoHatchBlockEntity::new, hatches(HatchType.DYNAMO, HatchType.DYNAMO_MV, HatchType.DYNAMO_HV, HatchType.DYNAMO_EV, HatchType.DYNAMO_IV)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeTurbineBlockEntity>> LARGE_TURBINE_BE = BLOCK_ENTITIES.register("large_steam_turbine",
            () -> new BlockEntityType<>(LargeTurbineBlockEntity::new, LARGE_STEAM_TURBINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeGasTurbineBlockEntity>> LARGE_GAS_TURBINE_BE = BLOCK_ENTITIES.register("large_gas_turbine",
            () -> new BlockEntityType<>(LargeGasTurbineBlockEntity::new, LARGE_GAS_TURBINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeHpTurbineBlockEntity>> LARGE_HP_TURBINE_BE = BLOCK_ENTITIES.register("large_hp_steam_turbine",
            () -> new BlockEntityType<>(LargeHpTurbineBlockEntity::new, LARGE_HP_TURBINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingArrayBlockEntity>> PROCESSING_ARRAY_BE = BLOCK_ENTITIES.register("processing_array",
            () -> new BlockEntityType<>(ProcessingArrayBlockEntity::new, PROCESSING_ARRAY.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CharcoalPileBlockEntity>> CHARCOAL_PILE_BE = BLOCK_ENTITIES.register("charcoal_pile",
            () -> new BlockEntityType<>(CharcoalPileBlockEntity::new, CHARCOAL_PILE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CleanroomBlockEntity>> CLEANROOM_BE = BLOCK_ENTITIES.register("cleanroom",
            () -> new BlockEntityType<>(CleanroomBlockEntity::new, CLEANROOM.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterPumpBlockEntity>> WATER_PUMP_BE = BLOCK_ENTITIES.register("water_pump",
            () -> new BlockEntityType<>(WaterPumpBlockEntity::new, blocks(WATER_PUMPS)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OilDrillBlockEntity>> OIL_DRILL_BE = BLOCK_ENTITIES.register("oil_drill",
            () -> new BlockEntityType<>(OilDrillBlockEntity::new, blocks(OIL_DRILLS)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OreDrillBlockEntity>> ORE_DRILL_BE = BLOCK_ENTITIES.register("ore_drill",
            () -> new BlockEntityType<>(OreDrillBlockEntity::new, blocks(ORE_DRILLS)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BackfillerBlockEntity>> BACKFILLER_BE = BLOCK_ENTITIES.register("concrete_backfiller",
            () -> new BlockEntityType<>(BackfillerBlockEntity::new, blocks(BACKFILLERS)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeatExchangerBlockEntity>> HEAT_EXCHANGER_BE = BLOCK_ENTITIES.register("heat_exchanger",
            () -> new BlockEntityType<>(HeatExchangerBlockEntity::new, HEAT_EXCHANGER.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<ItemBusMenu>> ITEM_BUS_MENU = MENUS.register("item_bus", () -> IMenuTypeExtension.create(ItemBusMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<FluidHatchMenu>> FLUID_HATCH_MENU = MENUS.register("fluid_hatch", () -> IMenuTypeExtension.create(FluidHatchMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<EbfMenu>> EBF_MENU = MENUS.register("electric_blast_furnace", () -> IMenuTypeExtension.create(EbfMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<EbfMenu>> VACUUM_FREEZER_MENU = MENUS.register("vacuum_freezer", () -> IMenuTypeExtension.create(EbfMenu::freezerClient));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> LARGE_BOILER_MENU = MENUS.register("large_boiler", () -> IMenuTypeExtension.create(ControllerMenu::boilerClient));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> LARGE_TURBINE_MENU = MENUS.register("large_steam_turbine", () -> IMenuTypeExtension.create(ControllerMenu::turbineClient));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> PROCESSING_ARRAY_MENU = MENUS.register("processing_array", () -> IMenuTypeExtension.create(ControllerMenu::processingArrayClient));
    public static final DeferredHolder<MenuType<?>, MenuType<EbfMenu>> CLEANROOM_MENU = MENUS.register("cleanroom", () -> IMenuTypeExtension.create(EbfMenu::cleanroomClient));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> DRILL_MENU = MENUS.register("drill", () -> IMenuTypeExtension.create(ControllerMenu::drillClient));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> HEAT_EXCHANGER_MENU = MENUS.register("heat_exchanger", () -> IMenuTypeExtension.create(ControllerMenu::heatExchangerClient));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("multi", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.multi"))
            .icon(() -> new ItemStack(EBF.get()))
            .displayItems((params, out) -> {
                TAB_ORDER.forEach(i -> out.accept(i.get()));
                ItemStack iron = new ItemStack(SOLDERING_IRON.get());
                out.accept(iron.copy());
                BatteryItem.setCharge(iron, SOLDERING_IRON.get().capacity);
                out.accept(iron);
                for (LargeTurbineLogic.Size s : LargeTurbineLogic.Size.values()) {
                    for (MaterialData.ToolStats m : MaterialData.toolStats().values()) if (m.turbine()) out.accept(ROTORS.get(s).get().create(m.id()));
                }
            })
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(MultiContent::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ITEM_BUS_BE.get(), ItemBusBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_HATCH_BE.get(), FluidHatchBlockEntity::fluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, FLUID_HATCH_BE.get(), FluidHatchBlockEntity::itemHandler);
    }
}
