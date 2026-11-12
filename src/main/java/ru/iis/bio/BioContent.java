package ru.iis.bio;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.bio.block.BioTissueBlock;
import ru.iis.bio.block.ColonyCoreBlock;
import ru.iis.bio.block.ColonyCoreBlockEntity;
import ru.iis.bio.block.FeedingMawBlock;
import ru.iis.bio.block.FeedingMawBlockEntity;
import ru.iis.bio.world.ShuttleSpawner;
import ru.iis.bio.block.HearthBlock;
import ru.iis.bio.block.HearthBlockEntity;
import ru.iis.bio.block.StationBlock;
import ru.iis.bio.block.StationBlockEntity;
import ru.iis.bio.fluid.BioFluids;
import ru.iis.bio.item.BioItems;
import ru.iis.bio.item.Butchery;
import ru.iis.bio.production.BioStationMenu;
import ru.iis.bio.production.Station;

/**
 * Биопуть: первый прототип (D64–D69) — ядро колонии на личном челноке, живая ткань, пасть-кормушка; срез Б-1 (D70) — ручная
 * биохимия очага: туши, массы, гель, N0/G0, подложка, ростовое ложе, выращенные органы. Текстуры, модели, звуки, рецепты и
 * переводы — tools/gen/bio.py. Пакет не зависит от индустрии (D36).
 */
public final class BioContent {
    private BioContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);

    /** Ядро: ПКМ — состояние. Ломается рукой (дольше, чем камень) и возвращается предметом, ткань при этом отмирает. */
    public static final DeferredBlock<ColonyCoreBlock> CORE = BLOCKS.registerBlock("colony_core", ColonyCoreBlock::new,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(4.0f, 20.0f)
                    .lightLevel(s -> s.getValue(ColonyCoreBlock.MOOD).light));
    public static final DeferredBlock<FeedingMawBlock> MAW = BLOCKS.registerBlock("feeding_maw", FeedingMawBlock::new,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(2.5f, 10.0f).noOcclusion()
                    .lightLevel(s -> s.getValue(FeedingMawBlock.CONNECTED) ? 4 : 0));
    /** Ткань: тонкий слой на гранях блоков, предмета нет. */
    public static final DeferredBlock<BioTissueBlock> TISSUE = BLOCKS.registerBlock("bio_tissue", BioTissueBlock::new,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(0.3f).noCollision().randomTicks()
                    .pushReaction(PushReaction.DESTROY).lightLevel(s -> 2));
    /** Обшивка аварийного челнока: очень прочная, в ранней игре бесполезна (CONCEPT §4.2). */
    public static final DeferredBlock<Block> SHUTTLE_HULL = BLOCKS.registerBlock("shuttle_hull", Block::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK).strength(50.0f, 1200.0f).requiresCorrectToolForDrops());

    /** Остекление кабины: тёмное, потрескавшееся. */
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> SHUTTLE_WINDOW = BLOCKS.registerBlock("shuttle_window",
            net.minecraft.world.level.block.TransparentBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).sound(net.minecraft.world.level.block.SoundType.GLASS).strength(5.0f, 600.0f).noOcclusion()
                    .isValidSpawn((s, l, pos, t) -> false).isRedstoneConductor((s, l, pos) -> false).isSuffocating((s, l, pos) -> false)
                    .isViewBlocking((s, l, pos) -> false));
    /** Двигатель челнока: обгоревшее сопло. */
    public static final DeferredBlock<Block> SHUTTLE_ENGINE = BLOCKS.registerBlock("shuttle_engine", Block::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK).strength(50.0f, 1200.0f).requiresCorrectToolForDrops());

    // --- срез Б-1: ручные места и живые места выращивания (D70) ------------------------------------------------------
    private static DeferredBlock<StationBlock> station(String id, Station st, java.util.function.UnaryOperator<net.minecraft.world.level.block.state.BlockBehaviour.Properties> props) {
        return BLOCKS.registerBlock(id, p -> new StationBlock(st, p), props);
    }

    /** W01 разделочная площадка. */
    public static final DeferredBlock<StationBlock> BUTCHER = station("butcher_block", Station.BUTCHER,
            p -> p.mapColor(MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).strength(2.0f).noOcclusion().ignitedByLava());
    /** W03 ступа с пестиком. */
    public static final DeferredBlock<StationBlock> MORTAR = station("stone_mortar", Station.MORTAR,
            p -> p.mapColor(MapColor.STONE).sound(net.minecraft.world.level.block.SoundType.STONE).strength(1.5f).noOcclusion());
    /** W04 каменный варочный сосуд (ставится на очаг). */
    public static final DeferredBlock<StationBlock> POT = station("stone_pot", Station.POT,
            p -> p.mapColor(MapColor.STONE).sound(net.minecraft.world.level.block.SoundType.STONE).strength(1.5f).noOcclusion());
    /** W05 очаг. */
    public static final DeferredBlock<HearthBlock> HEARTH = BLOCKS.registerBlock("stone_hearth", HearthBlock::new,
            p -> p.mapColor(MapColor.STONE).sound(net.minecraft.world.level.block.SoundType.STONE).strength(1.5f)
                    .lightLevel(s -> s.getValue(HearthBlock.LIT) ? 12 : 0));
    /** W06 мерный смесительный сосуд. */
    public static final DeferredBlock<StationBlock> MIXER = station("measuring_vessel", Station.MIXER,
            p -> p.mapColor(MapColor.STONE).sound(net.minecraft.world.level.block.SoundType.STONE).strength(1.5f).noOcclusion());
    /** Ручная ростовая подложка: ставится вплотную к живой ткани колонии, питается её N0. */
    public static final DeferredBlock<StationBlock> SUBSTRATE = station("hand_substrate", Station.SUBSTRATE,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(0.8f).noOcclusion());
    /** A01 ростовое ложе ULV. */
    public static final DeferredBlock<StationBlock> BED = station("growth_bed", Station.BED,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(1.5f).noOcclusion().lightLevel(s -> 3));
    /** W08 каменный пресс. */
    public static final DeferredBlock<StationBlock> PRESS = station("stone_press", Station.PRESS,
            p -> p.mapColor(MapColor.STONE).sound(net.minecraft.world.level.block.SoundType.STONE).strength(1.5f).noOcclusion());
    /** W10 фильтровальный сосуд. */
    public static final DeferredBlock<StationBlock> FILTER = station("filter_vessel", Station.FILTER,
            p -> p.mapColor(MapColor.STONE).sound(net.minecraft.world.level.block.SoundType.STONE).strength(1.5f).noOcclusion());
    /** A03 формовочное гнездо LV (D73). */
    public static final DeferredBlock<StationBlock> NEST = station("forming_nest", Station.NEST, p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(1.5f).noOcclusion().lightLevel(s -> 3));
    /** F06 питательная железа LV. */
    public static final DeferredBlock<StationBlock> GLAND = station("nutrient_gland", Station.GLAND, p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(1.5f).noOcclusion().lightLevel(s -> 2));
    /** F09 мембранный фильтр LV. */
    public static final DeferredBlock<StationBlock> MEMBRANE_FILTER = station("membrane_filter", Station.MEMBRANE_FILTER, p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(1.5f).noOcclusion());

    // --- черновик Б-2 (D148): отходная яма, органы LV–HV, живое хранилище, канал предметов, площадка вивария ---------------------
    public static final java.util.Map<Station, DeferredBlock<StationBlock>> STATIONS2 = new java.util.EnumMap<>(Station.class);
    public static final java.util.List<DeferredItem<BlockItem>> B2_ITEMS = new java.util.ArrayList<>();
    static {
        for (Station st : new Station[]{Station.SUMP, Station.LOOSENER, Station.COMPACTOR, Station.ORE_STOMACH, Station.CROSSBREEDER, Station.INCUBATOR,
                Station.ISOLATOR, Station.ACCELERATOR, Station.BIOREACTOR, Station.SEPARATOR}) {
            String id = st == Station.SUMP ? "waste_sump" : st.id;
            DeferredBlock<StationBlock> b = st == Station.SUMP
                    ? station(id, st, p -> p.mapColor(MapColor.STONE).sound(net.minecraft.world.level.block.SoundType.STONE).strength(1.5f).noOcclusion())
                    : station(id, st, p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(1.5f).noOcclusion().lightLevel(s -> 2));
            STATIONS2.put(st, b);
            B2_ITEMS.add(ITEMS.registerSimpleBlockItem(id, b));
        }
    }
    public static final DeferredBlock<ru.iis.bio.block.LivingStoreBlock> LIVING_STORE = BLOCKS.registerBlock("living_store", ru.iis.bio.block.LivingStoreBlock::new,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(1.5f));
    public static final DeferredBlock<ru.iis.bio.block.ItemChannelBlock> ITEM_CHANNEL = BLOCKS.registerBlock("peristaltic_channel", ru.iis.bio.block.ItemChannelBlock::new,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(0.8f).noOcclusion());
    /** Площадка вивария: рядом с ней (4 блока) проводится воздействие на животное. */
    public static final DeferredBlock<Block> VIVARIUM_PAD = BLOCKS.registerBlock("vivarium_pad", Block::new,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(1.0f));
    static {
        B2_ITEMS.add(ITEMS.registerSimpleBlockItem("living_store", LIVING_STORE));
        B2_ITEMS.add(ITEMS.registerSimpleBlockItem("peristaltic_channel", ITEM_CHANNEL));
        B2_ITEMS.add(ITEMS.registerSimpleBlockItem("vivarium_pad", VIVARIUM_PAD));
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ru.iis.bio.block.LivingStoreBlockEntity>> LIVING_STORE_BE = BLOCK_ENTITIES.register("living_store",
            () -> new BlockEntityType<>(ru.iis.bio.block.LivingStoreBlockEntity::new, LIVING_STORE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ru.iis.bio.block.ItemChannelBlockEntity>> ITEM_CHANNEL_BE = BLOCK_ENTITIES.register("peristaltic_channel",
            () -> new BlockEntityType<>(ru.iis.bio.block.ItemChannelBlockEntity::new, ITEM_CHANNEL.get()));

    public static final java.util.List<DeferredItem<BlockItem>> STATION_ITEMS = java.util.List.of(
            ITEMS.registerSimpleBlockItem("butcher_block", BUTCHER), ITEMS.registerSimpleBlockItem("stone_mortar", MORTAR),
            ITEMS.registerSimpleBlockItem("stone_pot", POT), ITEMS.registerSimpleBlockItem("stone_hearth", HEARTH),
            ITEMS.registerSimpleBlockItem("measuring_vessel", MIXER), ITEMS.registerSimpleBlockItem("hand_substrate", SUBSTRATE),
            ITEMS.registerSimpleBlockItem("growth_bed", BED), ITEMS.registerSimpleBlockItem("stone_press", PRESS),
            ITEMS.registerSimpleBlockItem("filter_vessel", FILTER), ITEMS.registerSimpleBlockItem("forming_nest", NEST),
            ITEMS.registerSimpleBlockItem("nutrient_gland", GLAND), ITEMS.registerSimpleBlockItem("membrane_filter", MEMBRANE_FILTER));

    public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem("colony_core", CORE);
    public static final DeferredItem<BlockItem> MAW_ITEM = ITEMS.registerSimpleBlockItem("feeding_maw", MAW);
    public static final DeferredItem<BlockItem> HULL_ITEM = ITEMS.registerSimpleBlockItem("shuttle_hull", SHUTTLE_HULL);
    public static final DeferredItem<BlockItem> WINDOW_ITEM = ITEMS.registerSimpleBlockItem("shuttle_window", SHUTTLE_WINDOW);
    public static final DeferredItem<BlockItem> ENGINE_ITEM = ITEMS.registerSimpleBlockItem("shuttle_engine", SHUTTLE_ENGINE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ColonyCoreBlockEntity>> CORE_BE = BLOCK_ENTITIES.register("colony_core",
            () -> new BlockEntityType<>(ColonyCoreBlockEntity::new, CORE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeedingMawBlockEntity>> MAW_BE = BLOCK_ENTITIES.register("feeding_maw",
            () -> new BlockEntityType<>(FeedingMawBlockEntity::new, MAW.get()));

    // --- сосуды (D72) -----------------------------------------------------------------------------------------------
    public static final java.util.Map<String, DeferredBlock<ru.iis.bio.block.VesselBlock>> VESSELS = new java.util.LinkedHashMap<>();
    public static final java.util.List<DeferredItem<BlockItem>> VESSEL_ITEMS = new java.util.ArrayList<>();
    static {
        for (ru.iis.bio.block.VesselBlock.Spec sp : ru.iis.bio.block.VesselBlock.SPECS) {
            DeferredBlock<ru.iis.bio.block.VesselBlock> b = BLOCKS.registerBlock(sp.id(), p -> new ru.iis.bio.block.VesselBlock(sp, p),
                    p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(0.8f).noOcclusion());
            VESSELS.put(sp.id(), b);
            VESSEL_ITEMS.add(ITEMS.registerSimpleBlockItem(sp.id(), b));
        }
    }
    /** Обратный клапан LV и ручной пережим ULV — участки сети с особым поведением (D73). */
    public static final DeferredBlock<ru.iis.bio.block.CheckValveBlock> CHECK_VALVE = BLOCKS.registerBlock("check_valve",
            p -> new ru.iis.bio.block.CheckValveBlock(ru.iis.bio.block.VesselBlock.CHECK_VALVE, p),
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(0.8f).noOcclusion());
    public static final DeferredBlock<ru.iis.bio.block.ClampBlock> CLAMP = BLOCKS.registerBlock("manual_clamp",
            p -> new ru.iis.bio.block.ClampBlock(ru.iis.bio.block.VesselBlock.CLAMP, p),
            p -> p.mapColor(MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).strength(0.8f).noOcclusion());
    static {
        VESSELS.put("check_valve", (DeferredBlock) CHECK_VALVE);
        VESSELS.put("manual_clamp", (DeferredBlock) CLAMP);
        VESSEL_ITEMS.add(ITEMS.registerSimpleBlockItem("check_valve", CHECK_VALVE));
        VESSEL_ITEMS.add(ITEMS.registerSimpleBlockItem("manual_clamp", CLAMP));
    }
    /** Живой резервуар LV 8 000 мБ и мышечный насос LV A07 (D73). */
    public static final DeferredBlock<ru.iis.bio.block.LivingTankBlock> LIVING_TANK = BLOCKS.registerBlock("living_tank", ru.iis.bio.block.LivingTankBlock::new,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(1.5f));
    public static final DeferredBlock<ru.iis.bio.block.MusclePumpBlock> MUSCLE_PUMP = BLOCKS.registerBlock("muscle_pump", ru.iis.bio.block.MusclePumpBlock::new,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(1.5f));
    static {
        VESSEL_ITEMS.add(ITEMS.registerSimpleBlockItem("living_tank", LIVING_TANK));
        VESSEL_ITEMS.add(ITEMS.registerSimpleBlockItem("muscle_pump", MUSCLE_PUMP));
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ru.iis.bio.block.LivingTankBlockEntity>> LIVING_TANK_BE = BLOCK_ENTITIES.register("living_tank",
            () -> new BlockEntityType<>(ru.iis.bio.block.LivingTankBlockEntity::new, LIVING_TANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ru.iis.bio.block.MusclePumpBlockEntity>> MUSCLE_PUMP_BE = BLOCK_ENTITIES.register("muscle_pump",
            () -> new BlockEntityType<>(ru.iis.bio.block.MusclePumpBlockEntity::new, MUSCLE_PUMP.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ru.iis.bio.block.VesselBlockEntity>> VESSEL_BE = BLOCK_ENTITIES.register("bio_vessel",
            () -> new BlockEntityType<>(ru.iis.bio.block.VesselBlockEntity::new, VESSELS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationBlockEntity>> STATION_BE = BLOCK_ENTITIES.register("bio_station",
            () -> new BlockEntityType<>(StationBlockEntity::new, java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(BUTCHER, MORTAR, POT, MIXER, SUBSTRATE, BED, PRESS, FILTER, NEST, GLAND, MEMBRANE_FILTER), STATIONS2.values().stream())
                    .map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HearthBlockEntity>> HEARTH_BE = BLOCK_ENTITIES.register("stone_hearth",
            () -> new BlockEntityType<>(HearthBlockEntity::new, HEARTH.get()));

    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<ru.iis.bio.production.HearthMenu>> HEARTH_MENU =
            MENUS.register("stone_hearth", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(ru.iis.bio.production.HearthMenu::client));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<BioStationMenu>> STATION_MENU =
            MENUS.register("bio_station", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(BioStationMenu::client));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("bio", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.bio"))
            .icon(() -> new ItemStack(CORE_ITEM.get()))
            .displayItems((params, out) -> {
                out.accept(CORE_ITEM.get());
                out.accept(MAW_ITEM.get());
                out.accept(HULL_ITEM.get());
                out.accept(WINDOW_ITEM.get());
                out.accept(ENGINE_ITEM.get());
                STATION_ITEMS.forEach(i -> out.accept(i.get()));
                VESSEL_ITEMS.forEach(i -> out.accept(i.get()));
                B2_ITEMS.forEach(i -> out.accept(i.get()));
                BioItems.TAB.forEach(i -> out.accept(i.get()));
            })
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        BioSounds.SOUNDS.register(modBus);
        MENUS.register(modBus);
        BioItems.register(modBus);
        BioFluids.register(modBus);
        ru.iis.bio.mutant.Mutants.register(modBus);
        NeoForge.EVENT_BUS.addListener(Butchery::onDrops);
        modBus.addListener(BioContent::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(ShuttleSpawner::onLogin);
        NeoForge.EVENT_BUS.addListener(ShuttleSpawner::onLogout);
        NeoForge.EVENT_BUS.addListener(ShuttleSpawner::onPlayerTick);
    }

    /** Органы, места и сосуды Био видны общему API жидкостей NeoForge (трубы, капсулы, резервуары) — D72. */
    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        var FLUID = net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK;
        event.registerBlockEntity(FLUID, STATION_BE.get(), (be, side) -> be.fluids);
        var ITEM = net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK;
        event.registerBlockEntity(ITEM, LIVING_STORE_BE.get(), (be, side) -> net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(be));
        event.registerBlockEntity(ITEM, ITEM_CHANNEL_BE.get(), (be, side) -> net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(be));
        event.registerBlockEntity(FLUID, CORE_BE.get(), (be, side) -> be.fluids());
        event.registerBlockEntity(FLUID, VESSEL_BE.get(), ru.iis.bio.block.VesselBlockEntity::fluidHandler);
        event.registerBlockEntity(FLUID, LIVING_TANK_BE.get(), ru.iis.bio.block.LivingTankBlockEntity::fluidHandler);
        event.registerBlockEntity(FLUID, MUSCLE_PUMP_BE.get(), ru.iis.bio.block.MusclePumpBlockEntity::fluidHandler);
    }
}
