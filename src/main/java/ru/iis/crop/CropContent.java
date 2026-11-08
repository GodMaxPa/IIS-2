package ru.iis.crop;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Грядки IC2 с селекцией, как в ИИС 1 (D139): грядка, мешочек семян, удобрение, гербицид, совок, анализатор, линза, урожай GT,
 * Crop-Matron и сборщик урожая. Органический репликатор GT — машина LV–UV в {@link ru.iis.lv.LvMachineType}.
 */
public final class CropContent {
    private CropContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IIS.MODID);
    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<CropSeedItem.Seed>> SEED =
            COMPONENTS.registerComponentType("crop_seed", b -> b.persistent(CropSeedItem.Seed.CODEC).networkSynchronized(CropSeedItem.Seed.STREAM_CODEC));

    public static final DeferredBlock<CropBlock> CROP = BLOCKS.registerBlock("crop", CropBlock::new,
            p -> p.mapColor(MapColor.PLANT).sound(SoundType.CROP).instabreak().noCollision().noOcclusion().noLootTable().pushReaction(PushReaction.DESTROY));
    public static final DeferredItem<BlockItem> CROP_STICKS = add(ITEMS.registerItem("crop_sticks", p -> new BlockItem(CROP.get(), p)));
    public static final DeferredItem<CropSeedItem> CROP_SEED = ITEMS.registerItem("crop_seed", CropSeedItem::new);
    public static final DeferredItem<Item> FERTILIZER = add(ITEMS.registerSimpleItem("fertilizer"));
    public static final DeferredItem<Item> WEED_EX = add(ITEMS.registerItem("weed_ex", p -> new Item(p.durability(64))));
    public static final DeferredItem<Item> WEEDING_TROWEL = add(ITEMS.registerItem("weeding_trowel", p -> new Item(p.stacksTo(1))));
    public static final DeferredItem<Item> CROP_LENS = add(ITEMS.registerItem("crop_lens", p -> new Item(p.stacksTo(1))));
    public static final DeferredItem<CropnalyzerItem> CROPNALYZER = add(ITEMS.registerItem("cropnalyzer", p -> new CropnalyzerItem(p.stacksTo(1))));
    public static final DeferredItem<Item> GRIN_POWDER = add(ITEMS.registerSimpleItem("grin_powder"));
    public static final DeferredItem<Item> COFFEE_BEANS = add(ITEMS.registerSimpleItem("coffee_beans"));
    public static final DeferredItem<Item> HOPS = add(ITEMS.registerSimpleItem("hops"));
    /** IC2: земляной нарост снимает все эффекты, как молоко. */
    public static final DeferredItem<Item> TERRA_WART = add(ITEMS.registerItem("terra_wart", p -> new Item(p.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).build(),
            Consumables.defaultFood().onConsume(ClearAllStatusEffectsConsumeEffect.INSTANCE).build()))));

    public static final DeferredBlock<CropMachineBlock> CROP_MATRON = machine("crop_matron", CropMachineBlockEntity.Kind.MATRON);
    public static final DeferredBlock<CropMachineBlock> CROP_HARVESTER = machine("crop_harvester", CropMachineBlockEntity.Kind.HARVESTER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CropBlockEntity>> CROP_BE = BLOCK_ENTITIES.register("crop",
            () -> new BlockEntityType<>(CropBlockEntity::new, CROP.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CropMachineBlockEntity>> CROP_MACHINE_BE = BLOCK_ENTITIES.register("crop_machine",
            () -> new BlockEntityType<>(CropMachineBlockEntity::new, CROP_MATRON.get(), CROP_HARVESTER.get()));

    /** Урожай GT (GT_MetaGenerated_Item_02:314-354): id → еда (сытость, насыщение) или ничего. */
    public static final List<DeferredItem<Item>> DROPS = new ArrayList<>();

    static {
        String[][] drops = {
                {"plumbilia_leaf"}, {"argentia_leaf"}, {"indigo_blossom"}, {"tea_leaf"}, {"oil_berry"}, {"bobs_yer_uncle_berry"}, {"uum_berry"}, {"uua_berry"},
                {"milk_wart"}, {"bauxia_leaf"}, {"titania_leaf"}, {"reactoria_leaf"}, {"uranium_leaf"}, {"thunder_leaf"}, {"nickelback_leaf"}, {"galvania_leaf"},
                {"pyrolusium_leaf"}, {"coppon_fiber"}, {"scheelinium_leaf"}, {"platina_leaf"}, {"quantaria_leaf"}, {"quantaria_leaf_osmium"}, {"stargatium_leaf"},
                {"tine_twig"}, {"chilly_pepper", "1", "0.3"}, {"lemon", "1", "0.3"}, {"tomato", "1", "0.2"}, {"max_tomato", "9", "1.0"}, {"grapes", "2", "0.3"},
                {"onion", "2", "0.2"}, {"cucumber", "1", "0.2"}, {"rape"}};
        for (String[] d : drops) {
            DeferredItem<Item> i = d.length == 1 ? ITEMS.registerSimpleItem(d[0])
                    : ITEMS.registerSimpleItem(d[0], p -> p.food(new FoodProperties.Builder().nutrition(Integer.parseInt(d[1])).saturationModifier(Float.parseFloat(d[2])).build()));
            DROPS.add(add(i));
        }
    }

    private static <I extends Item> DeferredItem<I> add(DeferredItem<I> i) {
        TAB_ORDER.add(i);
        return i;
    }

    private static DeferredBlock<CropMachineBlock> machine(String id, CropMachineBlockEntity.Kind kind) {
        DeferredBlock<CropMachineBlock> b = BLOCKS.registerBlock(id, p -> new CropMachineBlock(kind, p),
                p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops());
        add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("crops", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.crops"))
            .icon(() -> new ItemStack(CROP_STICKS.get()))
            .displayItems((params, out) -> {
                TAB_ORDER.forEach(i -> out.accept(i.get()));
                for (CropCard c : CropCard.ALL) out.accept(CropSeedItem.of(c, 1, 1, 1, 4));
            })
            .build());

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, CROP_MACHINE_BE.get(), (be, side) -> ru.iis.cover.Covers.items(be, side, be.itemHandler(side)));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, CROP_MACHINE_BE.get(), (be, side) -> be.kind == CropMachineBlockEntity.Kind.MATRON ? be.tank : null);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(CropContent::capabilities);
    }
}
