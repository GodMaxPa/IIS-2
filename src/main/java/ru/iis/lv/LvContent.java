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
import ru.iis.fluid.IISFluids;
import ru.iis.lv.block.*;
import ru.iis.lv.item.BatteryItem;
import ru.iis.lv.item.TreetapItem;
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

    // --- машины, генераторы, буферы, трансформатор ----------------------------------------------------------------
    public static final Map<LvMachineType, DeferredBlock<ElectricMachineBlock>> MACHINES = new EnumMap<>(LvMachineType.class);
    static {
        for (LvMachineType t : LvMachineType.values()) MACHINES.put(t, block(t.blockId(), p -> new ElectricMachineBlock(t, p), LvContent::metal));
    }
    public static final Map<GeneratorType, DeferredBlock<GeneratorBlock>> GENERATORS = new EnumMap<>(GeneratorType.class);
    static {
        for (GeneratorType t : GeneratorType.values()) GENERATORS.put(t, block(t.id, p -> new GeneratorBlock(t, p), LvContent::metal));
    }
    public static final DeferredBlock<BatteryBufferBlock> BATTERY_BUFFER_1X1 = block("battery_buffer_1x1_lv", p -> new BatteryBufferBlock(1, p), LvContent::metal);
    public static final DeferredBlock<BatteryBufferBlock> BATTERY_BUFFER_2X2 = block("battery_buffer_2x2_lv", p -> new BatteryBufferBlock(4, p), LvContent::metal);
    public static final DeferredBlock<TransformerBlock> TRANSFORMER = block("transformer_lv", TransformerBlock::new, LvContent::metal);

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
    public static final DeferredItem<Item> CIRCUIT_BASIC = item("circuit_basic");
    public static final DeferredItem<Item> VACUUM_TUBE = item("vacuum_tube");
    public static final DeferredItem<Item> COATED_CIRCUIT_BOARD = item("coated_circuit_board");
    public static final DeferredItem<Item> RESISTOR = item("resistor");
    public static final DeferredItem<Item> GLASS_TUBE = item("glass_tube");
    public static final DeferredItem<Item> DIAMOND_SAWBLADE = item("diamond_sawblade");
    public static final DeferredItem<Item> ITEM_FILTER = item("item_filter");
    public static final DeferredItem<Item> STEEL_ITEM_CASING = item("steel_item_casing");
    public static final DeferredItem<Item> PLATE_WOOD = item("plate_wood");
    public static final DeferredItem<Item> STICKY_RESIN = item("sticky_resin");
    public static final DeferredItem<Item> COAL_COKE = item("coal_coke");
    public static final DeferredItem<Item> WET_COKE_OVEN_BRICK = item("wet_coke_oven_brick");
    public static final DeferredItem<BatteryItem> BATTERY_LV = ITEMS.registerItem("battery_lv", p -> new BatteryItem(50000, 32, 1, p), p -> p.stacksTo(1));
    public static final DeferredItem<ru.iis.lv.item.MultimeterItem> MULTIMETER = ITEMS.registerItem("multimeter", ru.iis.lv.item.MultimeterItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<TreetapItem> TREETAP = ITEMS.registerItem("treetap", TreetapItem::new, p -> p.durability(16));
    static {
        TAB_ORDER.add(BATTERY_LV);
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
            () -> new BlockEntityType<>(BatteryBufferBlockEntity::new, BATTERY_BUFFER_1X1.get(), BATTERY_BUFFER_2X2.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransformerBlockEntity>> TRANSFORMER_BE = BLOCK_ENTITIES.register("transformer",
            () -> new BlockEntityType<>(TransformerBlockEntity::new, TRANSFORMER.get()));
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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("lv", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.lv"))
            .icon(() -> new ItemStack(MACHINES.get(LvMachineType.MACERATOR).get()))
            .displayItems((params, out) -> TAB_ORDER.forEach(i -> {
                out.accept(i.get());
                if (i == BATTERY_LV) {
                    ItemStack full = new ItemStack(BATTERY_LV.get());
                    BatteryItem.setCharge(full, BATTERY_LV.get().capacity);
                    out.accept(full);
                }
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
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ELECTRIC_MACHINE_BE.get(), ElectricMachineBlockEntity::fluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ELECTRIC_MACHINE_BE.get(), ElectricMachineBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, GENERATOR_BE.get(), GeneratorBlockEntity::fluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, GENERATOR_BE.get(), GeneratorBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, BATTERY_BUFFER_BE.get(), BatteryBufferBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, COKE_OVEN_BE.get(), CokeOvenBlockEntity::fluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, COKE_OVEN_BE.get(), CokeOvenBlockEntity::itemHandler);
    }
}
