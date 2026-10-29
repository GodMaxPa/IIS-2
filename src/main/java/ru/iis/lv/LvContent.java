package ru.iis.lv;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.energy.VoltageTier;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.block.*;
import ru.iis.core.material.MaterialData;
import ru.iis.lv.item.BatteryItem;
import ru.iis.lv.item.ElectricToolItem;
import ru.iis.lv.item.TreetapItem;
import ru.iis.tool.GtTools;
import ru.iis.lv.menu.BatteryBufferMenu;
import ru.iis.lv.menu.CokeOvenMenu;
import ru.iis.lv.menu.ElectricMachineMenu;
import ru.iis.lv.menu.GeneratorMenu;
import ru.iis.machine.block.MachineBlock;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Эпоха LV (срез 4): провода и кабели, машины LV, генераторы, буферы, трансформатор, детали, каучуковое дерево,
 * коксовая печь. Модели, переводы, лут, рецепты и мировая генерация — tools/gen/lv.py.
 */
public final class LvContent {
    private LvContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IIS.MODID);

    /** Заряд батарейки, EU. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> CHARGE = COMPONENTS.registerComponentType("charge",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** Ёмкость электроинструмента, EU: от батареи, с которой он собран (GT MaxCharge, D78). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> MAX_CHARGE = COMPONENTS.registerComponentType("max_charge",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** Конфигурация программной схемы GT, 0…24 (D75). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CIRCUIT_CONFIG = COMPONENTS.registerComponentType("circuit_config",
            b -> b.persistent(Codec.intRange(0, ru.iis.lv.item.ProgrammedCircuitItem.MAX)).networkSynchronized(ByteBufCodecs.VAR_INT));

    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();

    private static Block.Properties metal(Block.Properties p) {
        return p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Function<Block.Properties, B> f, UnaryOperator<Block.Properties> props) {
        DeferredBlock<B> b = BLOCKS.registerBlock(id, f, props);
        TAB_ORDER.add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    private static DeferredItem<Item> item(String id) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(id);
        TAB_ORDER.add(i);
        return i;
    }

    /** Программная схема GT (gt.integrated_circuit): выбирает рецепт машины, не расходуется. */
    public static final DeferredItem<Item> PROGRAMMED_CIRCUIT = ITEMS.registerItem("programmed_circuit", ru.iis.lv.item.ProgrammedCircuitItem::new);
    static {
        TAB_ORDER.add(PROGRAMMED_CIRCUIT);
    }

    // --- провода и кабели --------------------------------------------------------------------------------------
    public static final Map<String, DeferredBlock<CableBlock>> CABLES = new LinkedHashMap<>();
    static {
        for (CableSpecs.Spec s : CableSpecs.ALL) {
            CABLES.put(s.id(), block(s.id(), p -> new CableBlock(s, p),
                    p -> p.mapColor(MapColor.METAL).sound(s.insulated() ? SoundType.WOOL : SoundType.METAL).strength(1.0f, 3.0f).noOcclusion()));
        }
    }

    // --- корпуса -------------------------------------------------------------------------------------------------
    public static final DeferredBlock<Block> CASING_LV = block("casing_lv", Block::new, LvContent::metal);
    public static final DeferredBlock<Block> HULL_LV = block("hull_lv", Block::new, LvContent::metal);
    // MV (D81): GT_Block_Casings1 meta 2, Hull_MV
    public static final DeferredBlock<Block> CASING_MV = block("casing_mv", Block::new, LvContent::metal);
    public static final DeferredBlock<Block> HULL_MV = block("hull_mv", Block::new, LvContent::metal);
    // HV (D90): GT_Block_Casings1 meta 3
    public static final DeferredBlock<Block> CASING_HV = block("casing_hv", Block::new, LvContent::metal);
    // D92: Hull_HV (GT_Loader_MetaTileEntities:174, пластик)
    public static final DeferredBlock<Block> HULL_HV = block("hull_hv", Block::new, LvContent::metal);
    // D98: GT_Block_Casings1 meta 4, Hull_EV (титан, пластик)
    public static final DeferredBlock<Block> CASING_EV = block("casing_ev", Block::new, LvContent::metal);
    public static final DeferredBlock<Block> HULL_EV = block("hull_ev", Block::new, LvContent::metal);
    // D103: GT_Block_Casings1 meta 5, Hull_IV (вольфрамосталь, пластик, вольфрамовый кабель)
    public static final DeferredBlock<Block> CASING_IV = block("casing_iv", Block::new, LvContent::metal);
    public static final DeferredBlock<Block> HULL_IV = block("hull_iv", Block::new, LvContent::metal);

    // --- машины, генераторы, буферы, трансформатор ----------------------------------------------------------------
    public static final Map<LvMachineType, DeferredBlock<ElectricMachineBlock>> MACHINES = new EnumMap<>(LvMachineType.class);
    static {
        for (LvMachineType t : LvMachineType.values()) MACHINES.put(t, block(t.blockId(), p -> new ElectricMachineBlock(t, p), LvContent::metal));
    }
    public static final Map<GeneratorType, DeferredBlock<GeneratorBlock>> GENERATORS = new EnumMap<>(GeneratorType.class);
    static {
        for (GeneratorType t : GeneratorType.values()) GENERATORS.put(t, block(t.id, p -> new GeneratorBlock(t, p), LvContent::metal));
    }
    public static final DeferredBlock<BatteryBufferBlock> BATTERY_BUFFER_1X1 = block("battery_buffer_1x1_lv", p -> new BatteryBufferBlock(1, VoltageTier.LV, p), LvContent::metal);
    public static final DeferredBlock<BatteryBufferBlock> BATTERY_BUFFER_2X2 = block("battery_buffer_2x2_lv", p -> new BatteryBufferBlock(4, VoltageTier.LV, p), LvContent::metal);
    /** GT_Loader_MetaTileEntities:532, 554 Battery_Buffer_3by3_LV, 4by4_LV (D95). */
    public static final DeferredBlock<BatteryBufferBlock> BATTERY_BUFFER_3X3 = block("battery_buffer_3x3_lv", p -> new BatteryBufferBlock(9, VoltageTier.LV, p), LvContent::metal);
    public static final DeferredBlock<BatteryBufferBlock> BATTERY_BUFFER_4X4 = block("battery_buffer_4x4_lv", p -> new BatteryBufferBlock(16, VoltageTier.LV, p), LvContent::metal);
    /** GT_Loader_MetaTileEntities:489, 511, 533, 555 Battery_Buffer_*_MV (D95). */
    public static final java.util.List<DeferredBlock<BatteryBufferBlock>> BATTERY_BUFFERS_MV = java.util.stream.IntStream.of(1, 2, 3, 4)
            .mapToObj(n -> block("battery_buffer_" + n + "x" + n + "_mv", p -> new BatteryBufferBlock(n * n, VoltageTier.MV, p), LvContent::metal))
            .toList();
    /** GT_Loader_MetaTileEntities:491, 513, 535, 557 Battery_Buffer_*_EV (D99). */
    public static final java.util.List<DeferredBlock<BatteryBufferBlock>> BATTERY_BUFFERS_EV = java.util.stream.IntStream.of(1, 2, 3, 4)
            .mapToObj(n -> block("battery_buffer_" + n + "x" + n + "_ev", p -> new BatteryBufferBlock(n * n, VoltageTier.EV, p), LvContent::metal))
            .toList();
    /** GT_Loader_MetaTileEntities:503, 525, 547, 569 Battery_Buffer_*_IV (D103). */
    public static final java.util.List<DeferredBlock<BatteryBufferBlock>> BATTERY_BUFFERS_IV = java.util.stream.IntStream.of(1, 2, 3, 4)
            .mapToObj(n -> block("battery_buffer_" + n + "x" + n + "_iv", p -> new BatteryBufferBlock(n * n, VoltageTier.IV, p), LvContent::metal))
            .toList();
    /** GT_Loader_MetaTileEntities:490, 512, 534, 556 Battery_Buffer_*_HV (D94). */
    public static final java.util.List<DeferredBlock<BatteryBufferBlock>> BATTERY_BUFFERS_HV = java.util.stream.IntStream.of(1, 2, 3, 4)
            .mapToObj(n -> block("battery_buffer_" + n + "x" + n + "_hv", p -> new BatteryBufferBlock(n * n, VoltageTier.HV, p), LvContent::metal))
            .toList();
    public static final DeferredBlock<TransformerBlock> TRANSFORMER = block("transformer_lv", p -> new TransformerBlock(VoltageTier.LV, p), LvContent::metal);
    /** GT_Loader_MetaTileEntities:196 Transformer_HV_MV (D82). */
    public static final DeferredBlock<TransformerBlock> TRANSFORMER_MV = block("transformer_mv", p -> new TransformerBlock(VoltageTier.MV, p), LvContent::metal);
    /** GT_Loader_MetaTileEntities:197 Transformer_EV_HV (D94). */
    public static final DeferredBlock<TransformerBlock> TRANSFORMER_HV = block("transformer_hv", p -> new TransformerBlock(VoltageTier.HV, p), LvContent::metal);
    /** GT_Loader_MetaTileEntities:198 Transformer_IV_EV (D99). */
    public static final DeferredBlock<TransformerBlock> TRANSFORMER_EV = block("transformer_ev", p -> new TransformerBlock(VoltageTier.EV, p), LvContent::metal);

    // --- коксовая печь ---------------------------------------------------------------------------------------------
    public static final DeferredBlock<Block> COKE_OVEN_BRICKS = block("coke_oven_bricks", Block::new,
            p -> p.mapColor(MapColor.COLOR_BROWN).sound(SoundType.STONE).strength(2.0f, 6.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<CokeOvenBlock> COKE_OVEN = block("coke_oven", CokeOvenBlock::new,
            p -> p.mapColor(MapColor.COLOR_BROWN).sound(SoundType.STONE).strength(2.0f, 6.0f).requiresCorrectToolForDrops()
                    .lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 10 : 0));

    // --- каучуковое дерево -----------------------------------------------------------------------------------------
    public static final ResourceKey<ConfiguredFeature<?, ?>> RUBBER_TREE = ResourceKey.create(Registries.CONFIGURED_FEATURE,
            Identifier.fromNamespaceAndPath(IIS.MODID, "rubber_tree"));
    public static final TreeGrower RUBBER_GROWER = new TreeGrower("iis_rubber", Optional.empty(), Optional.of(RUBBER_TREE), Optional.empty());

    public static final DeferredBlock<RubberLogBlock> RUBBER_LOG = block("rubber_log", RubberLogBlock::new,
            p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2.0f).ignitedByLava().randomTicks());
    public static final DeferredBlock<TintedParticleLeavesBlock> RUBBER_LEAVES = block("rubber_leaves", p -> new TintedParticleLeavesBlock(0.01f, p),
            p -> p.mapColor(MapColor.PLANT).strength(0.2f).randomTicks().sound(SoundType.GRASS).noOcclusion()
                    .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos) -> false).ignitedByLava()
                    .pushReaction(PushReaction.DESTROY).isRedstoneConductor((s, l, pos) -> false));
    public static final DeferredBlock<SaplingBlock> RUBBER_SAPLING = block("rubber_sapling", p -> new SaplingBlock(RUBBER_GROWER, p),
            p -> p.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));

    // --- предметы --------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> ELECTRIC_MOTOR = item("electric_motor_lv");
    public static final DeferredItem<Item> ELECTRIC_PUMP = item("electric_pump_lv");
    public static final DeferredItem<Item> CONVEYOR_MODULE = item("conveyor_module_lv");
    public static final DeferredItem<Item> ELECTRIC_PISTON = item("electric_piston_lv");
    public static final DeferredItem<Item> ROBOT_ARM = item("robot_arm_lv");
    // MV (D81): GT_MetaGenerated_Item_01:483-629 детали MV, :695 Circuit_Good, :711 диод
    public static final DeferredItem<Item> ELECTRIC_MOTOR_MV = item("electric_motor_mv");
    public static final DeferredItem<Item> ELECTRIC_PUMP_MV = item("electric_pump_mv");
    public static final DeferredItem<Item> CONVEYOR_MODULE_MV = item("conveyor_module_mv");
    public static final DeferredItem<Item> ELECTRIC_PISTON_MV = item("electric_piston_mv");
    public static final DeferredItem<Item> ROBOT_ARM_MV = item("robot_arm_mv");
    public static final DeferredItem<Item> CIRCUIT_GOOD = item("circuit_good");
    public static final DeferredItem<Item> DIODE = item("diode");
    public static final DeferredItem<Item> CIRCUIT_BASIC = item("circuit_basic");
    public static final DeferredItem<Item> VACUUM_TUBE = item("vacuum_tube");
    public static final DeferredItem<Item> COATED_CIRCUIT_BOARD = item("coated_circuit_board");
    public static final DeferredItem<Item> RESISTOR = item("resistor");
    public static final DeferredItem<Item> GLASS_TUBE = item("glass_tube");
    public static final DeferredItem<Item> DIAMOND_SAWBLADE = item("diamond_sawblade");
    public static final DeferredItem<Item> ITEM_FILTER = item("item_filter");
    public static final DeferredItem<Item> STEEL_ITEM_CASING = item("steel_item_casing");
    public static final DeferredItem<Item> PLATE_WOOD = item("plate_wood");
    // формы отвердителя жидкостей GT (LV III, D76): лежат во входе и не расходуются
    public static final DeferredItem<Item> SHAPE_EMPTY = item("shape_empty");
    public static final DeferredItem<Item> MOLD_PLATE = item("mold_plate");
    public static final DeferredItem<Item> MOLD_GEAR = item("mold_gear");
    public static final DeferredItem<Item> MOLD_INGOT = item("mold_ingot");
    public static final DeferredItem<Item> MOLD_BALL = item("mold_ball");
    public static final DeferredItem<Item> MOLD_BLOCK = item("mold_block");
    public static final DeferredItem<Item> MOLD_NUGGET = item("mold_nugget");
    // D83: формы печи сплавов из ProcessingShaping и насадки экструдера GT (GT_MetaGenerated_Item_01:222-246); не расходуются
    public static final DeferredItem<Item> MOLD_CASING = item("mold_casing");
    public static final DeferredItem<Item> MOLD_BOTTLE = item("mold_bottle");
    public static final DeferredItem<Item> MOLD_ANVIL = item("mold_anvil");
    // формовочный пресс (D83): формы чеканки и наконечника стрелы, монета Doge, пустой стеклянный наконечник
    public static final DeferredItem<Item> MOLD_CREDIT = item("mold_credit");
    public static final DeferredItem<Item> MOLD_ARROW = item("mold_arrow");
    public static final DeferredItem<Item> COIN_DOGE = item("coin_doge");
    public static final DeferredItem<Item> ARROW_HEAD_GLASS_EMPTY = item("arrow_head_glass_empty");
    // D86: излучатели GT (GT_MetaGenerated_Item_01:656-657) для лазерного гравёра
    public static final DeferredItem<Item> EMITTER_LV = item("emitter_lv");
    public static final DeferredItem<Item> EMITTER_MV = item("emitter_mv");
    // D93: детали HV (GT_MetaGenerated_Item_01:496-669) и алмазная шлифовальная головка (:720) для измельчителя HV
    public static final DeferredItem<Item> ELECTRIC_MOTOR_HV = item("electric_motor_hv");
    public static final DeferredItem<Item> ELECTRIC_PUMP_HV = item("electric_pump_hv");
    public static final DeferredItem<Item> CONVEYOR_MODULE_HV = item("conveyor_module_hv");
    public static final DeferredItem<Item> ELECTRIC_PISTON_HV = item("electric_piston_hv");
    public static final DeferredItem<Item> ROBOT_ARM_HV = item("robot_arm_hv");
    public static final DeferredItem<Item> EMITTER_HV = item("emitter_hv");
    public static final DeferredItem<Item> GRINDER_DIAMOND = item("grinder_diamond");
    // D98: детали EV (GT_MetaGenerated_Item_01:485-631), конденсатор (:714), схема Elite (:699)
    public static final DeferredItem<Item> ELECTRIC_MOTOR_EV = item("electric_motor_ev");
    public static final DeferredItem<Item> ELECTRIC_PISTON_EV = item("electric_piston_ev");
    public static final DeferredItem<Item> CONVEYOR_MODULE_EV = item("conveyor_module_ev");
    public static final DeferredItem<Item> ROBOT_ARM_EV = item("robot_arm_ev");
    // D100: помпа и излучатель EV (GT_MetaGenerated_Item_01:504, :658)
    public static final DeferredItem<Item> ELECTRIC_PUMP_EV = item("electric_pump_ev");
    public static final DeferredItem<Item> EMITTER_EV = item("emitter_ev");
    // D103: детали IV (GT_MetaGenerated_Item_01:498–689) и датчики LV–IV (:685–689)
    public static final DeferredItem<Item> ELECTRIC_MOTOR_IV = item("electric_motor_iv");
    public static final DeferredItem<Item> ELECTRIC_PUMP_IV = item("electric_pump_iv");
    public static final DeferredItem<Item> CONVEYOR_MODULE_IV = item("conveyor_module_iv");
    public static final DeferredItem<Item> ELECTRIC_PISTON_IV = item("electric_piston_iv");
    public static final DeferredItem<Item> ROBOT_ARM_IV = item("robot_arm_iv");
    public static final DeferredItem<Item> EMITTER_IV = item("emitter_iv");
    public static final DeferredItem<Item> SENSOR_LV = item("sensor_lv");
    public static final DeferredItem<Item> SENSOR_MV = item("sensor_mv");
    public static final DeferredItem<Item> SENSOR_HV = item("sensor_hv");
    public static final DeferredItem<Item> SENSOR_EV = item("sensor_ev");
    public static final DeferredItem<Item> SENSOR_IV = item("sensor_iv");
    public static final DeferredItem<Item> CAPACITOR = item("capacitor");
    public static final DeferredItem<Item> CIRCUIT_ELITE = item("circuit_elite");
    // D94: малая катушка GT (GT_MetaGenerated_Item_03:50) — трансформатор HV
    public static final DeferredItem<Item> COIL_SMALL = item("coil_small");
    // D91: кремниевая буля, пластины и микросхемы GT (GT_MetaGenerated_Item_03:82-94)
    public static final DeferredItem<Item> SILICON_BOULE = item("silicon_boule");
    public static final DeferredItem<Item> SILICON_WAFER = item("silicon_wafer");
    public static final DeferredItem<Item> WAFER_ILC = item("wafer_ilc");
    public static final DeferredItem<Item> CHIP_ILC = item("chip_ilc");
    public static final DeferredItem<Item> WAFER_RAM = item("wafer_ram");
    public static final DeferredItem<Item> CHIP_RAM = item("chip_ram");
    // D92: транзистор, текстолитовая плата, хорошая интегральная схема, схема HV; растительный шарик и биоотходы (вместо IC2)
    public static final DeferredItem<Item> TRANSISTOR = item("transistor");
    public static final DeferredItem<Item> CIRCUIT_BOARD_PHENOLIC = item("circuit_board_phenolic");
    public static final DeferredItem<Item> CIRCUIT_INTEGRATED_GOOD = item("circuit_integrated_good");
    public static final DeferredItem<Item> CIRCUIT_ADVANCED = item("circuit_advanced");
    // D97: схема Data (GT_MetaGenerated_Item_01:698, уровень EV) и пластиковая печатная плата (Item_03:34)
    public static final DeferredItem<Item> CIRCUIT_DATA = item("circuit_data");
    public static final DeferredItem<Item> CIRCUIT_BOARD_PLASTIC = item("circuit_board_plastic");
    public static final DeferredItem<Item> PLANT_BALL = item("plant_ball");
    public static final DeferredItem<Item> BIO_CHAFF = item("bio_chaff");
    public static final List<String> EXTRUDER_SHAPE_IDS = List.of("plate", "rod", "bolt", "ring", "cell", "ingot", "wire", "casing",
            "pipe_tiny", "pipe_small", "pipe_medium", "pipe_large", "pipe_huge", "block", "sword", "pickaxe", "shovel", "axe", "hoe", "saw",
            "gear", "bottle", "small_gear");
    public static final Map<String, DeferredItem<Item>> EXTRUDER_SHAPES = new java.util.LinkedHashMap<>();
    static {
        for (String s : EXTRUDER_SHAPE_IDS) EXTRUDER_SHAPES.put(s, item("shape_extruder_" + s));
    }
    public static final DeferredItem<Item> STICKY_RESIN = item("sticky_resin");
    public static final DeferredItem<Item> COAL_COKE = item("coal_coke");
    public static final DeferredItem<Item> WET_COKE_OVEN_BRICK = item("wet_coke_oven_brick");
    // GT_MetaGenerated_Item_01:416-420 Battery_RE_LV_Sodium/Cadmium/Lithium: 50000 / 75000 / 100000 EU, 32 V
    public static final DeferredItem<BatteryItem> BATTERY_LV = ITEMS.registerItem("battery_lv", p -> new BatteryItem(50000, 32, 1, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_CADMIUM_LV = ITEMS.registerItem("battery_cadmium_lv", p -> new BatteryItem(75000, 32, 1, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_LITHIUM_LV = ITEMS.registerItem("battery_lithium_lv", p -> new BatteryItem(100000, 32, 1, p), p -> p.stacksTo(1));
    // D96: GT_MetaGenerated_Item_01:428-444 setElectricStats — MV 200 000 / 300 000 / 400 000 EU при 128 EU/t, HV ×4 при 512 EU/t
    public static final DeferredItem<Item> BATTERY_HULL_MV = item("battery_hull_mv");
    public static final DeferredItem<Item> BATTERY_HULL_HV = item("battery_hull_hv");
    public static final DeferredItem<BatteryItem> BATTERY_SODIUM_MV = ITEMS.registerItem("battery_sodium_mv", p -> new BatteryItem(200000, 128, 2, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_CADMIUM_MV = ITEMS.registerItem("battery_cadmium_mv", p -> new BatteryItem(300000, 128, 2, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_LITHIUM_MV = ITEMS.registerItem("battery_lithium_mv", p -> new BatteryItem(400000, 128, 2, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_SODIUM_HV = ITEMS.registerItem("battery_sodium_hv", p -> new BatteryItem(800000, 512, 3, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_CADMIUM_HV = ITEMS.registerItem("battery_cadmium_hv", p -> new BatteryItem(1200000, 512, 3, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_LITHIUM_HV = ITEMS.registerItem("battery_lithium_hv", p -> new BatteryItem(1600000, 512, 3, p), p -> p.stacksTo(1));
    // D101: одноразовые GT (GT_MetaGenerated_Item_01:410-436): кислотные 18000 / 72000 / 288000 и ртутные 32000 / 128000 / 512000 EU
    public static final DeferredItem<BatteryItem> BATTERY_ACID_LV = ITEMS.registerItem("battery_acid_lv", p -> new BatteryItem(18000, 32, 1, false, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_MERCURY_LV = ITEMS.registerItem("battery_mercury_lv", p -> new BatteryItem(32000, 32, 1, false, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_ACID_MV = ITEMS.registerItem("battery_acid_mv", p -> new BatteryItem(72000, 128, 2, false, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_MERCURY_MV = ITEMS.registerItem("battery_mercury_mv", p -> new BatteryItem(128000, 128, 2, false, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_ACID_HV = ITEMS.registerItem("battery_acid_hv", p -> new BatteryItem(288000, 512, 3, false, p), p -> p.stacksTo(1));
    public static final DeferredItem<BatteryItem> BATTERY_MERCURY_HV = ITEMS.registerItem("battery_mercury_hv", p -> new BatteryItem(512000, 512, 3, false, p), p -> p.stacksTo(1));
    /** Все батарейки — во вкладку пустыми и заряженными (D101: раньше там была только натриевая LV). */
    public static final List<DeferredItem<BatteryItem>> BATTERIES = List.of(BATTERY_LV, BATTERY_CADMIUM_LV, BATTERY_LITHIUM_LV, BATTERY_ACID_LV,
            BATTERY_MERCURY_LV, BATTERY_SODIUM_MV, BATTERY_CADMIUM_MV, BATTERY_LITHIUM_MV, BATTERY_ACID_MV, BATTERY_MERCURY_MV,
            BATTERY_SODIUM_HV, BATTERY_CADMIUM_HV, BATTERY_LITHIUM_HV, BATTERY_ACID_HV, BATTERY_MERCURY_HV);
    public static final DeferredItem<Item> BATTERY_HULL_LV = item("battery_hull_lv");
    // покрытия GT (ru.iis.cover.Covers.ITEMS); конвейер, помпа и манипулятор — сами детали LV
    public static final DeferredItem<Item> COVER_CONTROLLER = item("cover_controller");
    public static final DeferredItem<Item> COVER_ACTIVITY_DETECTOR = item("cover_activity_detector");
    public static final DeferredItem<Item> COVER_FLUID_DETECTOR = item("cover_fluid_detector");
    public static final DeferredItem<Item> COVER_ITEM_DETECTOR = item("cover_item_detector");
    public static final DeferredItem<Item> COVER_ENERGY_DETECTOR = item("cover_energy_detector");
    public static final DeferredItem<Item> COVER_CRAFTING = item("cover_crafting");
    public static final DeferredItem<Item> COVER_DRAIN = item("cover_drain");
    public static final DeferredItem<Item> COVER_SHUTTER = item("cover_shutter");
    // GT_MetaGenerated_Item_01:519, :531 — регулятор и фильтр жидкости (D80)
    public static final DeferredItem<Item> FLUID_REGULATOR_LV = item("fluid_regulator_lv");
    public static final DeferredItem<Item> COVER_FLUID_FILTER = item("cover_fluid_filter");
    public static final DeferredItem<ru.iis.lv.item.MultimeterItem> MULTIMETER = ITEMS.registerItem("multimeter", ru.iis.lv.item.MultimeterItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<TreetapItem> TREETAP = ITEMS.registerItem("treetap", TreetapItem::new, p -> p.durability(16));
    /** Электроинструменты LV из /iis/tools.json: бур, цепная пила, электроключ по материалам головки (D78). */
    public static final Map<String, DeferredItem<ElectricToolItem>> ELECTRIC_TOOLS = new LinkedHashMap<>();
    static {
        for (MaterialData.ToolDef d : MaterialData.tools()) {
            if (!GtTools.ELECTRIC.contains(d.tool())) continue;
            ELECTRIC_TOOLS.put(d.id(), ITEMS.registerItem(d.id(), p -> new ElectricToolItem(GtTools.properties(p, d), d)));
        }
    }
    static {
        TAB_ORDER.addAll(BATTERIES);
        TAB_ORDER.add(TREETAP);
        TAB_ORDER.add(MULTIMETER);
        TAB_ORDER.add(IISFluids.CREOSOTE_BUCKET);
    }

    // --- блок-сущности и меню --------------------------------------------------------------------------------------
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> CABLE_BE = BLOCK_ENTITIES.register("cable",
            () -> new BlockEntityType<>(CableBlockEntity::new, CABLES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricMachineBlockEntity>> ELECTRIC_MACHINE_BE = BLOCK_ENTITIES.register("electric_machine",
            () -> new BlockEntityType<>(ElectricMachineBlockEntity::new, MACHINES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlockEntity>> GENERATOR_BE = BLOCK_ENTITIES.register("generator",
            () -> new BlockEntityType<>(GeneratorBlockEntity::new, GENERATORS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBufferBlockEntity>> BATTERY_BUFFER_BE = BLOCK_ENTITIES.register("battery_buffer",
            () -> new BlockEntityType<>(BatteryBufferBlockEntity::new, java.util.stream.Stream.of(java.util.stream.Stream.of(BATTERY_BUFFER_1X1, BATTERY_BUFFER_2X2, BATTERY_BUFFER_3X3,
                    BATTERY_BUFFER_4X4), BATTERY_BUFFERS_MV.stream(), BATTERY_BUFFERS_HV.stream(), BATTERY_BUFFERS_EV.stream(), BATTERY_BUFFERS_IV.stream()).flatMap(x -> x)
                    .map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransformerBlockEntity>> TRANSFORMER_BE = BLOCK_ENTITIES.register("transformer",
            () -> new BlockEntityType<>(TransformerBlockEntity::new, TRANSFORMER.get(), TRANSFORMER_MV.get(), TRANSFORMER_HV.get(), TRANSFORMER_EV.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CokeOvenBlockEntity>> COKE_OVEN_BE = BLOCK_ENTITIES.register("coke_oven",
            () -> new BlockEntityType<>(CokeOvenBlockEntity::new, COKE_OVEN.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<ElectricMachineMenu>> ELECTRIC_MACHINE_MENU = MENUS.register("electric_machine",
            () -> IMenuTypeExtension.create(ElectricMachineMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<GeneratorMenu>> GENERATOR_MENU = MENUS.register("generator",
            () -> IMenuTypeExtension.create(GeneratorMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<BatteryBufferMenu>> BATTERY_BUFFER_MENU = MENUS.register("battery_buffer",
            () -> IMenuTypeExtension.create(BatteryBufferMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<CokeOvenMenu>> COKE_OVEN_MENU = MENUS.register("coke_oven",
            () -> IMenuTypeExtension.create(CokeOvenMenu::client));
    /** Интерфейс покрытия (D101). */
    public static final DeferredHolder<MenuType<?>, MenuType<ru.iis.cover.CoverMenu>> COVER_MENU = MENUS.register("cover",
            () -> IMenuTypeExtension.create(ru.iis.cover.CoverMenu::client));

    private static final String[] TAB_IDS = {"lv", "mv", "hv", "ev", "iv"};

    /** Вкладки по напряжению (D101, IV — D103): LV, MV, HV, EV, IV — уровень предмета по {@link #tabTier}. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = tierTab(0, LvMachineType.MACERATOR);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_MV = tierTab(1, LvMachineType.MACERATOR_MV);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_HV = tierTab(2, LvMachineType.MACERATOR_HV);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_EV = tierTab(3, LvMachineType.MACERATOR_EV);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_IV = tierTab(4, LvMachineType.MACERATOR_IV);

    private static DeferredHolder<CreativeModeTab, CreativeModeTab> tierTab(int tier, LvMachineType icon) {
        return TABS.register(TAB_IDS[tier], () -> {
            CreativeModeTab.Builder b = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.iis." + TAB_IDS[tier]))
                    .icon(() -> new ItemStack(MACHINES.get(icon).get()))
                    .displayItems((params, out) -> TAB_ORDER.forEach(i -> {
                        Item item = i.get();
                        if (tabTier(item) != tier) return;
                        // одноразовые — только заряженными (пустая — это уже выработанная), перезаряжаемые — пустой и заряженной
                        if (!(item instanceof BatteryItem bat) || bat.rechargeable) out.accept(item);
                        if (item instanceof BatteryItem bat) {
                            ItemStack full = new ItemStack(bat);
                            BatteryItem.setCharge(full, bat.capacity);
                            out.accept(full);
                        }
                    }));
            if (tier > 0) b.withTabsBefore(Identifier.fromNamespaceAndPath(IIS.MODID, TAB_IDS[tier - 1]));
            return b.build();
        });
    }

    /** Предметы без суффикса уровня: эпоха, в которой они нужны (схемы GT: Good — MV, Advanced — HV, Data и Elite — EV). */
    private static final Map<String, Integer> TAB_TIER_IDS = Map.ofEntries(
            Map.entry("circuit_good", 1), Map.entry("diode", 1), Map.entry("mold_credit", 1), Map.entry("mold_arrow", 1),
            Map.entry("coin_doge", 1), Map.entry("arrow_head_glass_empty", 1),
            Map.entry("circuit_advanced", 2), Map.entry("transistor", 2), Map.entry("circuit_board_phenolic", 2),
            Map.entry("circuit_integrated_good", 2), Map.entry("silicon_boule", 2), Map.entry("silicon_wafer", 2),
            Map.entry("wafer_ilc", 2), Map.entry("chip_ilc", 2), Map.entry("wafer_ram", 2), Map.entry("chip_ram", 2),
            Map.entry("coil_small", 2), Map.entry("grinder_diamond", 2), Map.entry("plant_ball", 2), Map.entry("bio_chaff", 2),
            Map.entry("circuit_data", 3), Map.entry("circuit_board_plastic", 3), Map.entry("capacitor", 3), Map.entry("circuit_elite", 3));

    /** Уровень вкладки 0…4 (LV…IV): суффикс _lv/_mv/_hv/_ev/_iv, у кабелей — напряжение, насадки экструдера — MV, иначе по таблице или LV. */
    public static int tabTier(Item item) {
        return tabTier(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath());
    }

    public static int tabTier(String id) {
        for (CableSpecs.Spec s : CableSpecs.ALL) {
            if (s.id().equals(id)) return s.voltage() <= 32 ? 0 : s.voltage() <= 128 ? 1 : s.voltage() <= 512 ? 2 : s.voltage() <= 2048 ? 3 : 4;
        }
        for (int t = TAB_IDS.length - 1; t >= 0; t--) if (id.endsWith("_" + TAB_IDS[t])) return t;
        if (id.startsWith("shape_extruder_")) return 1;
        return TAB_TIER_IDS.getOrDefault(id, 0);
    }

    /** Вкладка «Инструменты» LV: электроинструменты заряженными до 100 000 EU (литиевая батарея). */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TOOLS_TAB = TABS.register("electric_tools", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.electric_tools"))
            .icon(() -> ELECTRIC_TOOLS.values().stream().findFirst().map(t -> new ItemStack(t.get())).orElse(new ItemStack(BATTERY_LV.get())))
            .displayItems((params, out) -> ELECTRIC_TOOLS.values().forEach(t -> {
                ItemStack s = new ItemStack(t.get());
                s.set(MAX_CHARGE.get(), 100000L);
                BatteryItem.setCharge(s, 100000L);
                out.accept(s);
            }))
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(LvContent::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(Deferred::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ElectricToolItem::onCrafted);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ELECTRIC_MACHINE_BE.get(), (be, side) -> ru.iis.cover.Covers.fluids(be, side, be.fluidHandler(side)));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ELECTRIC_MACHINE_BE.get(), (be, side) -> ru.iis.cover.Covers.items(be, side, be.itemHandler(side)));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, GENERATOR_BE.get(), (be, side) -> ru.iis.cover.Covers.fluids(be, side, be.fluidHandler(side)));
        event.registerBlockEntity(Capabilities.Item.BLOCK, GENERATOR_BE.get(), (be, side) -> ru.iis.cover.Covers.items(be, side, be.itemHandler(side)));
        event.registerBlockEntity(Capabilities.Item.BLOCK, BATTERY_BUFFER_BE.get(), (be, side) -> ru.iis.cover.Covers.items(be, side, be.itemHandler(side)));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, COKE_OVEN_BE.get(), CokeOvenBlockEntity::fluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, COKE_OVEN_BE.get(), CokeOvenBlockEntity::itemHandler);
    }
}
