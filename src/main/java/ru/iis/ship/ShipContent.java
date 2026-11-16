package ru.iis.ship;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.bio.BioSounds;
import ru.iis.ship.entity.Brute;
import ru.iis.ship.entity.Crawler;
import ru.iis.ship.entity.Overmind;

/**
 * Материнский корабль (D156): блоки корпуса, грузовой лифт, существа корабля и структура мира.
 * Вид, названия, добыча и размещение в мире — данные из {@code tools/gen/ship.py}.
 */
public final class ShipContent {
    private ShipContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(IIS.MODID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, IIS.MODID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);

    /** Обшивка: корабль держался щитами, поэтому корпус из обычного сплава — добывается железной киркой (CONCEPT §5.2). */
    public static final DeferredBlock<Block> PLATING = BLOCKS.registerBlock("ship_plating", Block::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).sound(SoundType.NETHERITE_BLOCK).strength(6.0f, 30.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> DECK = BLOCKS.registerBlock("ship_deck", Block::new,
            p -> p.mapColor(MapColor.STONE).sound(SoundType.COPPER).strength(4.0f, 12.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> BULKHEAD = BLOCKS.registerBlock("ship_bulkhead", Block::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).sound(SoundType.COPPER).strength(4.0f, 12.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> LIGHT = BLOCKS.registerBlock("ship_light", Block::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).sound(SoundType.GLASS).strength(2.0f, 6.0f).lightLevel(s -> 13));
    /** Дикий нарост: заражение корабля. Не принадлежит ни одной колонии и сам не растёт. */
    public static final DeferredBlock<Block> GROWTH = BLOCKS.registerBlock("wild_growth", Block::new,
            p -> p.mapColor(MapColor.CRIMSON_NYLIUM).sound(BioSounds.FLESH).strength(1.2f, 2.0f).lightLevel(s -> 3));
    public static final DeferredBlock<LiftBlock> LIFT = BLOCKS.registerBlock("ship_lift", LiftBlock::new,
            p -> p.mapColor(MapColor.COLOR_YELLOW).sound(SoundType.NETHERITE_BLOCK).strength(6.0f, 30.0f).requiresCorrectToolForDrops().lightLevel(s -> 6));

    public static final DeferredItem<BlockItem> PLATING_ITEM = ITEMS.registerSimpleBlockItem(PLATING);
    public static final DeferredItem<BlockItem> DECK_ITEM = ITEMS.registerSimpleBlockItem(DECK);
    public static final DeferredItem<BlockItem> BULKHEAD_ITEM = ITEMS.registerSimpleBlockItem(BULKHEAD);
    public static final DeferredItem<BlockItem> LIGHT_ITEM = ITEMS.registerSimpleBlockItem(LIGHT);
    public static final DeferredItem<BlockItem> GROWTH_ITEM = ITEMS.registerSimpleBlockItem(GROWTH);
    public static final DeferredItem<BlockItem> LIFT_ITEM = ITEMS.registerSimpleBlockItem(LIFT);

    /** Ползун: слабое существо первого и третьего ярусов. */
    public static final DeferredHolder<EntityType<?>, EntityType<Crawler>> CRAWLER = ENTITIES.registerEntityType("ship_crawler",
            Crawler::new, MobCategory.MONSTER, b -> b.sized(0.9F, 0.7F).clientTrackingRange(8));
    /** Супермутант второго яруса. */
    public static final DeferredHolder<EntityType<?>, EntityType<Brute>> BRUTE = ENTITIES.registerEntityType("ship_brute",
            Brute::new, MobCategory.MONSTER, b -> b.sized(1.2F, 2.3F).clientTrackingRange(8));
    /** Ядро сверхорганизма: босс второго яруса. */
    public static final DeferredHolder<EntityType<?>, EntityType<Overmind>> OVERMIND = ENTITIES.registerEntityType("ship_overmind",
            Overmind::new, MobCategory.MONSTER, b -> b.sized(2.4F, 2.6F).clientTrackingRange(10).fireImmune());

    public static final DeferredHolder<StructureType<?>, StructureType<MotherShipStructure>> STRUCTURE =
            STRUCTURE_TYPES.register("mother_ship", () -> () -> MotherShipStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE =
            PIECE_TYPES.register("mother_ship", () -> (StructurePieceType.ContextlessType) MotherShipPiece::new);

    /** Вкладка подсистемы: показывается через «Био» (CreativeTabs.category). */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("ship", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.ship"))
            .icon(() -> new ItemStack(PLATING_ITEM.get()))
            .displayItems((params, out) -> {
                out.accept(PLATING_ITEM.get());
                out.accept(DECK_ITEM.get());
                out.accept(BULKHEAD_ITEM.get());
                out.accept(LIGHT_ITEM.get());
                out.accept(LIFT_ITEM.get());
                out.accept(GROWTH_ITEM.get());
            }).build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        PIECE_TYPES.register(modBus);
        TABS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(CRAWLER.get(), Crawler.createAttributes().build());
            e.put(BRUTE.get(), Brute.createAttributes().build());
            e.put(OVERMIND.get(), Overmind.createAttributes().build());
        });
    }
}
