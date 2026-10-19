package ru.iis.multi;

import net.minecraft.core.registries.Registries;
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
import ru.iis.multi.menu.EbfMenu;
import ru.iis.multi.menu.FluidHatchMenu;
import ru.iis.multi.menu.ItemBusMenu;

import java.util.*;
import java.util.function.Supplier;

/**
 * Многоблочные машины: электрическая доменная печь, люки, корпуса, катушки, каркасы, паяльник.
 * Модели, переводы и рецепты — tools/gen/ebf.py.
 */
public final class MultiContent {
    private MultiContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);

    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();

    private static Block.Properties metal(Block.Properties p) {
        return p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, java.util.function.Function<Block.Properties, B> f, java.util.function.UnaryOperator<Block.Properties> props) {
        DeferredBlock<B> b = BLOCKS.registerBlock(id, f, props);
        TAB_ORDER.add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    public static final List<String> FRAME_MATERIALS = List.of("invar", "steel", "iron", "bronze", "aluminium");
    public static final Map<String, DeferredBlock<Block>> FRAMES = new LinkedHashMap<>();
    static {
        for (String m : FRAME_MATERIALS) {
            FRAMES.put(m, block("frame_" + m, Block::new, p -> metal(p).noOcclusion().strength(3.0f, 6.0f)));
        }
    }
    public static final DeferredBlock<Block> HEAT_PROOF_CASING = block("heat_proof_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> COIL_CUPRONICKEL = block("coil_cupronickel", Block::new, MultiContent::metal);
    public static final Map<HatchType, DeferredBlock<HatchBlock>> HATCHES = new EnumMap<>(HatchType.class);
    static {
        for (HatchType t : HatchType.values()) HATCHES.put(t, block(t.id, p -> new HatchBlock(t, p), MultiContent::metal));
    }
    public static final DeferredBlock<EbfBlock> EBF = block("electric_blast_furnace", EbfBlock::new,
            p -> metal(p).lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 13 : 0));

    public static final DeferredItem<SolderingIronItem> SOLDERING_IRON = ITEMS.registerItem("soldering_iron_lv", SolderingIronItem::new, p -> p.stacksTo(1));

    private static Block[] hatches(HatchType... types) {
        return Arrays.stream(types).map(t -> HATCHES.get(t).get()).toArray(Block[]::new);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyHatchBlockEntity>> ENERGY_HATCH_BE = BLOCK_ENTITIES.register("energy_hatch",
            () -> new BlockEntityType<>(EnergyHatchBlockEntity::new, hatches(HatchType.ENERGY)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemBusBlockEntity>> ITEM_BUS_BE = BLOCK_ENTITIES.register("item_bus",
            () -> new BlockEntityType<>(ItemBusBlockEntity::new, hatches(HatchType.INPUT_BUS, HatchType.OUTPUT_BUS)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidHatchBlockEntity>> FLUID_HATCH_BE = BLOCK_ENTITIES.register("fluid_hatch",
            () -> new BlockEntityType<>(FluidHatchBlockEntity::new, hatches(HatchType.INPUT_HATCH, HatchType.OUTPUT_HATCH)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaintenanceHatchBlockEntity>> MAINTENANCE_HATCH_BE = BLOCK_ENTITIES.register("maintenance_hatch",
            () -> new BlockEntityType<>(MaintenanceHatchBlockEntity::new, hatches(HatchType.MAINTENANCE)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EbfBlockEntity>> EBF_BE = BLOCK_ENTITIES.register("electric_blast_furnace",
            () -> new BlockEntityType<>(EbfBlockEntity::new, EBF.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<ItemBusMenu>> ITEM_BUS_MENU = MENUS.register("item_bus", () -> IMenuTypeExtension.create(ItemBusMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<FluidHatchMenu>> FLUID_HATCH_MENU = MENUS.register("fluid_hatch", () -> IMenuTypeExtension.create(FluidHatchMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<EbfMenu>> EBF_MENU = MENUS.register("electric_blast_furnace", () -> IMenuTypeExtension.create(EbfMenu::client));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("multi", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.multi"))
            .icon(() -> new ItemStack(EBF.get()))
            .displayItems((params, out) -> {
                TAB_ORDER.forEach(i -> out.accept(i.get()));
                ItemStack iron = new ItemStack(SOLDERING_IRON.get());
                out.accept(iron.copy());
                BatteryItem.setCharge(iron, SOLDERING_IRON.get().capacity);
                out.accept(iron);
            })
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(MultiContent::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ITEM_BUS_BE.get(), ItemBusBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_HATCH_BE.get(), FluidHatchBlockEntity::fluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, FLUID_HATCH_BE.get(), FluidHatchBlockEntity::itemHandler);
    }
}
