package ru.iis.space;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Космос, первый этап (D115): Луна, кислород, ракета 1-го уровня. Числа и рецепты — Galacticraft 3.0.12 сборки ИИС 1
 * (config/Galacticraft, scripts/Galacticraft.zs); код и текстуры свои: лицензия GC запрещает распространять его материалы.
 */
public final class SpaceContent {
    private SpaceContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(IIS.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IIS.MODID);

    public static final ResourceKey<Level> MOON = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(IIS.MODID, "moon"));
    public static final ResourceKey<Level> MARS = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(IIS.MODID, "mars"));
    public static final ResourceKey<Level> ASTEROIDS = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(IIS.MODID, "asteroids"));
    public static final ResourceKey<DamageType> OXYGEN_SUFFOCATION = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(IIS.MODID, "oxygen_suffocation"));

    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();

    private static Block.Properties rock(Block.Properties p) {
        return p.mapColor(MapColor.STONE).sound(SoundType.STONE).strength(1.5f, 2.5f).requiresCorrectToolForDrops();
    }

    private static Block.Properties soil(Block.Properties p) {
        return p.mapColor(MapColor.STONE).sound(SoundType.GRAVEL).strength(0.5f);
    }

    private static Block.Properties metal(Block.Properties p) {
        return p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Function<Block.Properties, B> f, java.util.function.UnaryOperator<Block.Properties> props) {
        DeferredBlock<B> b = BLOCKS.registerBlock(id, f, props);
        TAB_ORDER.add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    private static DeferredItem<Item> item(String id) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(id);
        TAB_ORDER.add(i);
        return i;
    }

    public static final DeferredBlock<Block> MOON_ROCK = block("moon_rock", Block::new, SpaceContent::rock);
    public static final DeferredBlock<Block> MOON_DIRT = block("moon_dirt", Block::new, SpaceContent::soil);
    public static final DeferredBlock<Block> MOON_TURF = block("moon_turf", Block::new, SpaceContent::soil);
    public static final DeferredBlock<Block> MARS_ROCK = block("mars_rock", Block::new, SpaceContent::rock);
    public static final DeferredBlock<Block> MARS_DIRT = block("mars_dirt", Block::new, SpaceContent::soil);
    public static final DeferredBlock<Block> MARS_TURF = block("mars_turf", Block::new, SpaceContent::soil);
    public static final DeferredBlock<LaunchPadBlock> LAUNCH_PAD = block("launch_pad", LaunchPadBlock::new, p -> metal(p).noOcclusion());
    public static final DeferredBlock<FuelLoaderBlock> FUEL_LOADER = block("fuel_loader", FuelLoaderBlock::new, SpaceContent::metal);
    public static final DeferredBlock<NasaWorkbenchBlock> NASA_WORKBENCH = block("nasa_workbench", NasaWorkbenchBlock::new, SpaceContent::metal);
    // D134: астероиды и машины третьего этапа
    public static final DeferredBlock<Block> ASTEROID_ROCK = block("asteroid_rock", Block::new, SpaceContent::rock);
    public static final DeferredBlock<SpaceMachineBlock> OXYGEN_COLLECTOR = block("oxygen_collector", p -> new SpaceMachineBlock(SpaceMachineBlock.Kind.COLLECTOR, p), SpaceContent::metal);
    public static final DeferredBlock<SpaceMachineBlock> OXYGEN_DISTRIBUTOR = block("oxygen_distributor", p -> new SpaceMachineBlock(SpaceMachineBlock.Kind.DISTRIBUTOR, p), SpaceContent::metal);
    public static final DeferredBlock<SpaceMachineBlock> CARGO_LOADER = block("cargo_loader", p -> new SpaceMachineBlock(SpaceMachineBlock.Kind.LOADER, p), SpaceContent::metal);
    public static final DeferredBlock<SpaceMachineBlock> CARGO_UNLOADER = block("cargo_unloader", p -> new SpaceMachineBlock(SpaceMachineBlock.Kind.UNLOADER, p), SpaceContent::metal);

    public static final DeferredItem<Item> SCHEMATIC_3X3 = item("schematic_3x3");
    public static final DeferredItem<Item> COMPRESSED_IRON = item("compressed_iron");
    public static final DeferredItem<Item> COMPRESSED_STEEL = item("compressed_steel");
    public static final DeferredItem<Item> COMPRESSED_ALUMINIUM = item("compressed_aluminium");
    public static final DeferredItem<Item> COMPRESSED_TIN = item("compressed_tin");
    public static final DeferredItem<Item> COMPRESSED_COPPER = item("compressed_copper");
    public static final DeferredItem<Item> COMPRESSED_BRONZE = item("compressed_bronze");
    public static final DeferredItem<Item> COMPRESSED_TITANIUM = item("compressed_titanium");
    public static final DeferredItem<Item> COMPRESSED_METEORIC_IRON = item("compressed_meteoric_iron");
    public static final DeferredItem<Item> COMPRESSED_DESH = item("compressed_desh");
    public static final DeferredItem<Item> HEAVY_INGOT_T3 = item("heavy_ingot_t3");
    public static final DeferredItem<Item> HEAVY_PLATING_T3 = item("heavy_plating_t3");
    public static final DeferredItem<Item> HEAVY_ROCKET_ENGINE = item("heavy_rocket_engine");
    public static final DeferredItem<Item> HEAVY_ROCKET_FINS = item("heavy_rocket_fins");
    public static final DeferredItem<Item> HEAVY_NOSE_CONE = item("heavy_nose_cone");
    public static final DeferredItem<Item> ROCKET_BOOSTER = item("rocket_booster");
    public static final DeferredItem<Item> HEAVY_INGOT_T1 = item("heavy_ingot_t1");
    public static final DeferredItem<Item> HEAVY_PLATING = item("heavy_plating");
    public static final DeferredItem<Item> HEAVY_INGOT_T2 = item("heavy_ingot_t2");
    public static final DeferredItem<Item> HEAVY_PLATING_T2 = item("heavy_plating_t2");
    public static final DeferredItem<Item> ROCKET_ENGINE_T1 = item("rocket_engine_t1");
    public static final DeferredItem<Item> ROCKET_FINS = item("rocket_fins");
    public static final DeferredItem<Item> NOSE_CONE = item("nose_cone");
    public static final DeferredItem<Item> CANVAS = item("canvas");
    public static final DeferredItem<Item> PARACHUTE = item("parachute");
    public static final DeferredItem<Item> WAFER_BASIC = item("wafer_basic");
    public static final DeferredItem<Item> WAFER_ADVANCED = item("wafer_advanced");
    public static final DeferredItem<Item> AIR_VENT = item("air_vent");
    public static final DeferredItem<Item> AIR_FAN = item("air_fan");
    public static final DeferredItem<Item> OXYGEN_CONCENTRATOR = item("oxygen_concentrator");
    public static final DeferredItem<Item> OXYGEN_PIPE = item("oxygen_pipe");
    public static final DeferredItem<Item> CANISTER_TIN = item("canister_tin");
    public static final DeferredItem<Item> LARGE_STEEL_CELL = item("large_steel_cell");
    public static final DeferredItem<OxygenItem> OXYGEN_MASK = register("oxygen_mask", p -> new OxygenItem(p.stacksTo(1)));
    public static final DeferredItem<OxygenItem> OXYGEN_GEAR = register("oxygen_gear", p -> new OxygenItem(p.stacksTo(1)));
    /** GC: малый баллон — 900 единиц, по одной в секунду; 0 повреждения — полный. */
    public static final DeferredItem<OxygenItem> OXYGEN_TANK_LIGHT = register("oxygen_tank_light", p -> new OxygenItem(p.durability(OxygenSystem.TANK_LIGHT)));

    private static <I extends Item> DeferredItem<I> register(String id, Function<Item.Properties, I> f) {
        DeferredItem<I> i = ITEMS.registerItem(id, f);
        TAB_ORDER.add(i);
        return i;
    }

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<RocketItem.Stored>> ROCKET_DATA =
            COMPONENTS.registerComponentType("rocket_data", b -> b.persistent(RocketItem.Stored.CODEC).networkSynchronized(RocketItem.Stored.STREAM_CODEC));
    public static final DeferredItem<RocketItem> ROCKET_T1 = register("rocket_t1", p -> new RocketItem(1, p.stacksTo(1)));
    public static final DeferredItem<RocketItem> ROCKET_T2 = register("rocket_t2", p -> new RocketItem(2, p.stacksTo(1)));
    public static final DeferredItem<RocketItem> ROCKET_T3 = register("rocket_t3", p -> new RocketItem(3, p.stacksTo(1)));

    public static Item rocketItem(int tier) {
        return tier >= 3 ? ROCKET_T3.get() : tier == 2 ? ROCKET_T2.get() : ROCKET_T1.get();
    }

    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = ENTITIES.registerEntityType("rocket", RocketEntity::new, MobCategory.MISC,
            b -> b.noLootTable().sized(1.6f, 6.0f).passengerAttachments(1.5f).clientTrackingRange(16).updateInterval(1));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelLoaderBlockEntity>> FUEL_LOADER_BE = BLOCK_ENTITIES.register("fuel_loader",
            () -> new BlockEntityType<>(FuelLoaderBlockEntity::new, FUEL_LOADER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpaceMachineBlockEntity>> SPACE_MACHINE_BE = BLOCK_ENTITIES.register("space_machine",
            () -> new BlockEntityType<>(SpaceMachineBlockEntity::new, OXYGEN_COLLECTOR.get(), OXYGEN_DISTRIBUTOR.get(), CARGO_LOADER.get(), CARGO_UNLOADER.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<NasaWorkbenchMenu>> NASA_MENU = MENUS.register("nasa_workbench",
            () -> new MenuType<>(NasaWorkbenchMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<RocketMenu>> ROCKET_MENU = MENUS.register("rocket",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(RocketMenu::client));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("space", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.space"))
            .icon(() -> new ItemStack(ROCKET_T1.get()))
            .displayItems((params, out) -> TAB_ORDER.forEach(i -> out.accept(i.get())))
            .build());

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FUEL_LOADER_BE.get(), (be, side) -> be.tank);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SPACE_MACHINE_BE.get(), (be, side) -> be.kind() == SpaceMachineBlock.Kind.COLLECTOR || be.kind() == SpaceMachineBlock.Kind.DISTRIBUTOR ? be.tank : null);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(SpaceContent::capabilities);
        NeoForge.EVENT_BUS.addListener(OxygenSystem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(LowGravity::onJoin);
        NeoForge.EVENT_BUS.addListener(RocketEntity::onDismount);
        ru.iis.gametest.SpaceLiveCheck.init();
    }
}
