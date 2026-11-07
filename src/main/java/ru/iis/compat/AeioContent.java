package ru.iis.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.List;

/**
 * Предметы цепочки AE2 и Ender IO из ИИС 1, которых нет в новых модах (D125): семена и чистые кристаллы (нового AE2 они не знают,
 * рецепты GT_MachineRecipeLoader:395–401, 641–643), плотная стальная пластина для прессов AE2.zs, пластины светокамня и обсидиана.
 */
public final class AeioContent {
    private AeioContent() {}

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    private static final List<DeferredItem<? extends Item>> ORDER = new ArrayList<>();

    private static DeferredItem<Item> item(String id) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(id);
        ORDER.add(i);
        return i;
    }

    public static final DeferredItem<Item> SEED_CERTUS = item("crystal_seed_certus");
    public static final DeferredItem<Item> SEED_NETHER = item("crystal_seed_nether");
    public static final DeferredItem<Item> SEED_FLUIX = item("crystal_seed_fluix");
    public static final DeferredItem<Item> PURE_CERTUS = item("crystal_pure_certus");
    public static final DeferredItem<Item> PURE_NETHER = item("crystal_pure_nether");
    public static final DeferredItem<Item> PURE_FLUIX = item("crystal_pure_fluix");
    public static final DeferredItem<Item> PLATE_DENSE_STEEL = item("plate_dense_steel");
    /** D127: Extra-Cells.zs, корпус ячеек 256K. */
    public static final DeferredItem<Item> CELL_HOUSING_ADVANCED = item("cell_housing_advanced");
    public static final DeferredItem<Item> PLATE_GLOWSTONE = item("plate_glowstone");
    public static final DeferredItem<Item> PLATE_OBSIDIAN = item("plate_obsidian");
    /** D135: малая пружина из красного сплава для электролампы EnderIO.zs (GT ProcessingWire:45). */
    public static final DeferredItem<Item> SPRING_SMALL_RED_ALLOY = item("spring_small_red_alloy");

    // D135: вещи Ender IO 2.3 из ИИС 1, которых нет в новом Ender IO
    private static <B extends net.minecraft.world.level.block.Block> net.neoforged.neoforge.registries.DeferredBlock<B> block(String id, java.util.function.Function<net.minecraft.world.level.block.Block.Properties, B> f) {
        var b = BLOCKS.registerBlock(id, f, p -> p.mapColor(net.minecraft.world.level.material.MapColor.METAL).sound(net.minecraft.world.level.block.SoundType.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops());
        ORDER.add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    public static final net.neoforged.neoforge.registries.DeferredBlock<EioBlocks.BufferBlock> ITEM_BUFFER = block("item_buffer", EioBlocks.BufferBlock::new);
    public static final net.neoforged.neoforge.registries.DeferredBlock<EioBlocks.CapBankBlock> CAP_BANK_BASIC = block("capacitor_bank_basic", EioBlocks.CapBankBlock::new);
    public static final net.neoforged.neoforge.registries.DeferredBlock<EioBlocks.LightBlock> ELECTRIC_LIGHT = block("electric_light", p -> new EioBlocks.LightBlock(true, p));
    public static final net.neoforged.neoforge.registries.DeferredBlock<EioBlocks.LightBlock> BASIC_LIGHT = block("basic_light", p -> new EioBlocks.LightBlock(false, p));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<EioBlocks.BufferEntity>> BUFFER_BE =
            BLOCK_ENTITIES.register("item_buffer", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(EioBlocks.BufferEntity::new, ITEM_BUFFER.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<EioBlocks.PoweredEntity>> POWERED_BE =
            BLOCK_ENTITIES.register("eio_powered", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(EioBlocks.PoweredEntity::new, CAP_BANK_BASIC.get(), ELECTRIC_LIGHT.get()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("aeio", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.aeio"))
            .icon(() -> new ItemStack(PURE_FLUIX.get()))
            .displayItems((params, out) -> ORDER.forEach(i -> out.accept(i.get())))
            .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, BUFFER_BE.get(), (be, side) -> net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(be));
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, POWERED_BE.get(), (be, side) -> be.energy);
        });
    }
}
