package ru.iis.rail;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.rail.block.*;
import ru.iis.rail.cart.ElectricLocomotive;
import ru.iis.rail.cart.LocomotiveItem;
import ru.iis.rail.cart.SteamLocomotive;
import ru.iis.rail.menu.LocomotiveMenu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Железная дорога по Railcraft 9.12 сборки ИИС 1 (D102), заход 1: детали путей, пути пяти типов с ускорителями,
 * крестовинами, стрелками и тройниками, переводные рычаг и привод, упор, блокирующий и сцепной пути, монтировки и
 * сцепка вагонеток. Модели, переводы и рецепты (по GT5 и скрипту RailCraft.zs ИИС 1) — tools/gen/rail.py.
 */
public final class RailContent {
    private RailContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);

    /** Монтировки Railcraft и монтировки GT (GT5: GT_MetaGenerated_Tool реализует IToolCrowbar). */
    public static final TagKey<Item> CROWBARS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IIS.MODID, "crowbars"));

    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();
    /** Все пути ИИС по id (для окраски и тестов). */
    public static final Map<String, DeferredBlock<? extends Block>> TRACKS = new LinkedHashMap<>();

    private static Block.Properties rail(Block.Properties p) {
        return p.noCollision().strength(0.7f).sound(SoundType.METAL).pushReaction(PushReaction.NORMAL);
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Function<Block.Properties, B> f, UnaryOperator<Block.Properties> props) {
        DeferredBlock<B> b = BLOCKS.registerBlock(id, f, props);
        TAB_ORDER.add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    private static <B extends Block> void track(String id, Function<Block.Properties, B> f, UnaryOperator<Block.Properties> props) {
        TRACKS.put(id, block(id, f, props));
    }

    private static DeferredItem<Item> item(String id) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(id);
        TAB_ORDER.add(i);
        return i;
    }

    // --- детали (Railcraft part.tie, part.railbed, part.rail, part.rebar, part.circuit) --------------------------
    public static final DeferredItem<Item> TIE_WOOD = item("tie_wood");
    public static final DeferredItem<Item> TIE_STONE = item("tie_stone");
    public static final DeferredItem<Item> RAILBED_WOOD = item("railbed_wood");
    public static final DeferredItem<Item> RAILBED_STONE = item("railbed_stone");
    public static final DeferredItem<Item> RAIL_STANDARD = item("rail_standard");
    public static final DeferredItem<Item> RAIL_ADVANCED = item("rail_advanced");
    public static final DeferredItem<Item> RAIL_WOODEN = item("rail_wooden");
    public static final DeferredItem<Item> RAIL_HIGH_SPEED = item("rail_high_speed");
    public static final DeferredItem<Item> RAIL_REINFORCED = item("rail_reinforced");
    public static final DeferredItem<Item> RAIL_ELECTRIC = item("rail_electric");
    public static final DeferredItem<Item> REBAR = item("rebar");
    public static final DeferredItem<Item> CIRCUIT_CONTROLLER = item("circuit_controller");
    public static final DeferredItem<Item> CIRCUIT_RECEIVER = item("circuit_receiver");
    public static final DeferredItem<Item> CIRCUIT_SIGNAL = item("circuit_signal");
    /** Railcraft ItemCrowbar: железная 250, усиленная (стальная) 500 применений. */
    public static final DeferredItem<Item> CROWBAR_RED = ITEMS.registerSimpleItem("crowbar_red", p -> p.durability(250));
    public static final DeferredItem<Item> CROWBAR_REINFORCED = ITEMS.registerSimpleItem("crowbar_reinforced", p -> p.durability(500));
    static {
        TAB_ORDER.add(CROWBAR_RED);
        TAB_ORDER.add(CROWBAR_REINFORCED);
    }

    // --- пути ---------------------------------------------------------------------------------------------------
    static {
        for (TrackKind k : TrackKind.values()) {
            String base = k == TrackKind.STANDARD ? "track" : "track_" + k.id;
            // усиленный путь не боится взрывов (Railcraft TrackReinforced)
            UnaryOperator<Block.Properties> props = k == TrackKind.REINFORCED ? p -> rail(p).explosionResistance(1200f) : RailContent::rail;
            if (k != TrackKind.STANDARD) track(base, p -> new TrackBlock(k, p), props);
            if (k == TrackKind.WOODEN || k == TrackKind.REINFORCED || k == TrackKind.HIGH_SPEED) track(base + "_booster", p -> new BoosterTrackBlock(k, false, p), props);
            if (k == TrackKind.HIGH_SPEED) track(base + "_transition", p -> new BoosterTrackBlock(k, true, p), props);
            if (k != TrackKind.HIGH_SPEED) track(base + "_junction", p -> new JunctionTrackBlock(k, p), props);
            track(base + "_switch", p -> new SwitchTrackBlock(k, p), props);
            track(base + "_wye", p -> new WyeTrackBlock(k, p), props);
        }
        track("track_buffer_stop", p -> new LogicTrackBlock(LogicTrackBlock.Type.BUFFER_STOP, p), p -> p.strength(0.7f).sound(SoundType.METAL).noOcclusion());
        track("track_locking", p -> new LogicTrackBlock(LogicTrackBlock.Type.LOCKING, p), RailContent::rail);
        track("track_coupler", p -> new LogicTrackBlock(LogicTrackBlock.Type.COUPLER, p), RailContent::rail);
    }
    public static final DeferredBlock<SwitchActuatorBlock> SWITCH_LEVER = block("switch_lever", p -> new SwitchActuatorBlock(false, p),
            p -> p.mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<SwitchActuatorBlock> SWITCH_MOTOR = block("switch_motor", p -> new SwitchActuatorBlock(true, p),
            p -> p.mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.METAL).noOcclusion());

    /** Электрический фидер Railcraft: питает сеть электрических путей от проводов GT. */
    public static final DeferredBlock<ElectricFeederBlock> ELECTRIC_FEEDER = block("electric_feeder", ElectricFeederBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFeederBlockEntity>> FEEDER_BE = BLOCK_ENTITIES.register("electric_feeder",
            () -> new BlockEntityType<>(ElectricFeederBlockEntity::new, ELECTRIC_FEEDER.get()));

    // --- локомотивы (заход 2) -----------------------------------------------------------------------------------
    public static final DeferredHolder<EntityType<?>, EntityType<SteamLocomotive>> STEAM_LOCO = ENTITIES.registerEntityType("loco_steam",
            SteamLocomotive::new, MobCategory.MISC, b -> b.noLootTable().sized(0.98f, 0.7f).passengerAttachments(0.1875f).clientTrackingRange(8));
    public static final DeferredHolder<EntityType<?>, EntityType<ElectricLocomotive>> ELECTRIC_LOCO = ENTITIES.registerEntityType("loco_electric",
            ElectricLocomotive::new, MobCategory.MISC, b -> b.noLootTable().sized(0.98f, 0.7f).passengerAttachments(0.1875f).clientTrackingRange(8));
    public static final DeferredItem<LocomotiveItem> LOCO_STEAM = ITEMS.registerItem("loco_steam", p -> new LocomotiveItem(STEAM_LOCO, p.stacksTo(1)));
    public static final DeferredItem<LocomotiveItem> LOCO_ELECTRIC = ITEMS.registerItem("loco_electric", p -> new LocomotiveItem(ELECTRIC_LOCO, p.stacksTo(1)));
    static {
        TAB_ORDER.add(LOCO_STEAM);
        TAB_ORDER.add(LOCO_ELECTRIC);
    }
    public static final DeferredHolder<MenuType<?>, MenuType<LocomotiveMenu>> LOCO_MENU = MENUS.register("locomotive",
            () -> IMenuTypeExtension.create(LocomotiveMenu::client));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("rail", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.rail"))
            .icon(() -> new ItemStack(TRACKS.get("track_high_speed").get()))
            .displayItems((params, out) -> {
                out.accept(Items.RAIL);
                out.accept(Items.POWERED_RAIL);
                out.accept(Items.DETECTOR_RAIL);
                out.accept(Items.ACTIVATOR_RAIL);
                TAB_ORDER.forEach(i -> out.accept(i.get()));
            })
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        NeoForge.EVENT_BUS.addListener(RailEvents::onEntityTick);
        NeoForge.EVENT_BUS.addListener(RailEvents::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(RailEvents::onEntityLeave);
    }
}
