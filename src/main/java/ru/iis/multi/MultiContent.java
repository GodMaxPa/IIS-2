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

    public static final List<String> FRAME_MATERIALS = List.of("invar", "steel", "iron", "bronze", "aluminium", "blue_steel", "titanium");
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
    /** GT_Block_Casings2 meta 1: морозостойкий корпус вакуумного морозильника (D81). */
    public static final DeferredBlock<Block> FROST_PROOF_CASING = block("frost_proof_casing", Block::new, MultiContent::metal);
    public static final Map<HatchType, DeferredBlock<HatchBlock>> HATCHES = new EnumMap<>(HatchType.class);
    static {
        for (HatchType t : HatchType.values()) HATCHES.put(t, block(t.id, p -> new HatchBlock(t, p), MultiContent::metal));
    }
    public static final DeferredBlock<EbfBlock> EBF = block("electric_blast_furnace", EbfBlock::new,
            p -> metal(p).lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 13 : 0));
    public static final DeferredBlock<EbfBlock> VACUUM_FREEZER = block("vacuum_freezer", p -> new EbfBlock(p, VacuumFreezerBlockEntity::new), MultiContent::metal);

    /** Корпуса больших котлов (D88): GT_Block_Casings1:10, Casings2:0, 12, 13, Casings3:13, 14. */
    public static final DeferredBlock<Block> BRONZE_PLATED_BRICKS = block("bronze_plated_bricks", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> BRONZE_PIPE_CASING = block("bronze_pipe_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> BRONZE_FIREBOX_CASING = block("bronze_firebox_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> SOLID_STEEL_CASING = block("solid_steel_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> STEEL_PIPE_CASING = block("steel_pipe_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> STEEL_FIREBOX_CASING = block("steel_firebox_casing", Block::new, MultiContent::metal);
    /** D97: GT_Block_Casings4:2 стабильный титановый корпус, Casings2:14 титановые трубы, Casings4:3 титановая топка. */
    public static final DeferredBlock<Block> STABLE_TITANIUM_CASING = block("stable_titanium_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> TITANIUM_PIPE_CASING = block("titanium_pipe_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<Block> TITANIUM_FIREBOX_CASING = block("titanium_firebox_casing", Block::new, MultiContent::metal);
    /** Большие котлы GT (GT_Loader_MetaTileEntities:1152–1154); вольфрамостальной придёт с вольфрамосталью. */
    public static final Map<LargeBoilerLogic.Tier, DeferredBlock<LargeBoilerBlock>> LARGE_BOILERS = new EnumMap<>(LargeBoilerLogic.Tier.class);
    static {
        for (LargeBoilerLogic.Tier t : List.of(LargeBoilerLogic.Tier.BRONZE, LargeBoilerLogic.Tier.STEEL, LargeBoilerLogic.Tier.TITANIUM)) {
            LARGE_BOILERS.put(t, block("large_boiler_" + t.name().toLowerCase(Locale.ROOT), p -> new LargeBoilerBlock(p, t),
                    p -> metal(p).lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 13 : 0)));
        }
    }

    /** GT_Block_Casings4:9 — турбинный корпус (D89); большая паровая турбина GT_Loader_MetaTileEntities:1243. */
    public static final DeferredBlock<Block> TURBINE_CASING = block("turbine_casing", Block::new, MultiContent::metal);
    public static final DeferredBlock<EbfBlock> LARGE_STEAM_TURBINE = block("large_steam_turbine", p -> new EbfBlock(p, LargeTurbineBlockEntity::new), MultiContent::metal);

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
            default -> new LargeBoilerBlockEntity.Casings(BRONZE_PLATED_BRICKS.get(), BRONZE_PIPE_CASING.get(), BRONZE_FIREBOX_CASING.get());
        };
    }

    public static final DeferredItem<SolderingIronItem> SOLDERING_IRON = ITEMS.registerItem("soldering_iron_lv", SolderingIronItem::new, p -> p.stacksTo(1));

    private static Block[] hatches(HatchType... types) {
        return Arrays.stream(types).map(t -> HATCHES.get(t).get()).toArray(Block[]::new);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyHatchBlockEntity>> ENERGY_HATCH_BE = BLOCK_ENTITIES.register("energy_hatch",
            () -> new BlockEntityType<>(EnergyHatchBlockEntity::new, hatches(HatchType.ENERGY, HatchType.ENERGY_MV, HatchType.ENERGY_HV)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemBusBlockEntity>> ITEM_BUS_BE = BLOCK_ENTITIES.register("item_bus",
            () -> new BlockEntityType<>(ItemBusBlockEntity::new, hatches(HatchType.INPUT_BUS, HatchType.OUTPUT_BUS)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidHatchBlockEntity>> FLUID_HATCH_BE = BLOCK_ENTITIES.register("fluid_hatch",
            () -> new BlockEntityType<>(FluidHatchBlockEntity::new, hatches(HatchType.INPUT_HATCH, HatchType.OUTPUT_HATCH)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaintenanceHatchBlockEntity>> MAINTENANCE_HATCH_BE = BLOCK_ENTITIES.register("maintenance_hatch",
            () -> new BlockEntityType<>(MaintenanceHatchBlockEntity::new, hatches(HatchType.MAINTENANCE)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EbfBlockEntity>> EBF_BE = BLOCK_ENTITIES.register("electric_blast_furnace",
            () -> new BlockEntityType<>(EbfBlockEntity::new, EBF.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VacuumFreezerBlockEntity>> VACUUM_FREEZER_BE = BLOCK_ENTITIES.register("vacuum_freezer",
            () -> new BlockEntityType<>(VacuumFreezerBlockEntity::new, VACUUM_FREEZER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeBoilerBlockEntity>> LARGE_BOILER_BE = BLOCK_ENTITIES.register("large_boiler",
            () -> new BlockEntityType<>(LargeBoilerBlockEntity::new, LARGE_BOILERS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DynamoHatchBlockEntity>> DYNAMO_HATCH_BE = BLOCK_ENTITIES.register("dynamo_hatch",
            () -> new BlockEntityType<>(DynamoHatchBlockEntity::new, hatches(HatchType.DYNAMO, HatchType.DYNAMO_MV, HatchType.DYNAMO_HV)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeTurbineBlockEntity>> LARGE_TURBINE_BE = BLOCK_ENTITIES.register("large_steam_turbine",
            () -> new BlockEntityType<>(LargeTurbineBlockEntity::new, LARGE_STEAM_TURBINE.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<ItemBusMenu>> ITEM_BUS_MENU = MENUS.register("item_bus", () -> IMenuTypeExtension.create(ItemBusMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<FluidHatchMenu>> FLUID_HATCH_MENU = MENUS.register("fluid_hatch", () -> IMenuTypeExtension.create(FluidHatchMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<EbfMenu>> EBF_MENU = MENUS.register("electric_blast_furnace", () -> IMenuTypeExtension.create(EbfMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<EbfMenu>> VACUUM_FREEZER_MENU = MENUS.register("vacuum_freezer", () -> IMenuTypeExtension.create(EbfMenu::freezerClient));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> LARGE_BOILER_MENU = MENUS.register("large_boiler", () -> IMenuTypeExtension.create(ControllerMenu::boilerClient));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> LARGE_TURBINE_MENU = MENUS.register("large_steam_turbine", () -> IMenuTypeExtension.create(ControllerMenu::turbineClient));

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
