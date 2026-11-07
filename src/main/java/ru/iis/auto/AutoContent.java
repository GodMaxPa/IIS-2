package ru.iis.auto;

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
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.energy.VoltageTier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Автоматика, хранилища, автобур и помпа, настраиваемый трансформатор (D108). */
public final class AutoContent {
    private AutoContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IIS.MODID);

    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();
    private static final List<VoltageTier> LV_IV = List.of(VoltageTier.LV, VoltageTier.MV, VoltageTier.HV, VoltageTier.EV, VoltageTier.IV);
    /** D110, D112, D121: автоматика и отпугиватели есть до UV. */
    private static final List<VoltageTier> LV_ZPM = List.of(VoltageTier.LV, VoltageTier.MV, VoltageTier.HV, VoltageTier.EV, VoltageTier.IV, VoltageTier.LUV, VoltageTier.ZPM, VoltageTier.UV);

    private static Block.Properties metal(Block.Properties p) {
        return p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Function<Block.Properties, B> f) {
        DeferredBlock<B> b = BLOCKS.registerBlock(id, f, AutoContent::metal);
        TAB_ORDER.add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    private static String t(VoltageTier v) {
        return v.name().toLowerCase(Locale.ROOT);
    }

    /** Автоматика: {@code <вид>_<уровень>}. */
    public static final Map<AutomationType, List<DeferredBlock<AutomationBlock>>> AUTOMATION = new EnumMap<>(AutomationType.class);
    static {
        for (AutomationType k : AutomationType.values()) {
            List<DeferredBlock<AutomationBlock>> l = new ArrayList<>();
            for (VoltageTier v : LV_ZPM) l.add(block(k.id + "_" + t(v), p -> new AutomationBlock(k, v, p)));
            AUTOMATION.put(k, l);
        }
    }

    /** GT CommonSizeCompute и QuantumTank.getCapacity. */
    public static final List<DeferredBlock<QuantumChestBlock>> QUANTUM_CHESTS = new ArrayList<>();
    static {
        int[] caps = {4000000, 8000000, 16000000, 32000000, 64000000};
        for (int i = 0; i < 5; i++) {
            int c = caps[i];
            QUANTUM_CHESTS.add(block("quantum_chest_" + t(LV_IV.get(i)), p -> new QuantumChestBlock(c, p)));
        }
    }

    public static final List<DeferredBlock<BasicDrillerBlock>> MINERS = LV_IV.subList(0, 3).stream()
            .map(v -> block("miner_" + t(v), p -> new BasicDrillerBlock(false, v, p))).toList();
    public static final List<DeferredBlock<BasicDrillerBlock>> PUMPS = LV_IV.stream()
            .map(v -> block("pump_" + t(v), p -> new BasicDrillerBlock(true, v, p))).toList();
    public static final List<DeferredBlock<AdjustableTransformerBlock>> ADJUSTABLE_TRANSFORMERS = List.of(VoltageTier.EV, VoltageTier.IV, VoltageTier.LUV, VoltageTier.ZPM, VoltageTier.UV, VoltageTier.MAX).stream()
            .map(v -> block("adjustable_transformer_" + t(v), p -> new AdjustableTransformerBlock(v, p))).toList();

    public static final List<DeferredBlock<MonsterRepellentBlock>> MONSTER_REPELLENTS = LV_ZPM.stream()
            .map(v -> block("monster_repellent_" + t(v), p -> new MonsterRepellentBlock(v, p))).toList();

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<QuantumChestBlockEntity.Content>> QUANTUM_CONTENT =
            COMPONENTS.registerComponentType("quantum_content", b -> b.persistent(QuantumChestBlockEntity.Content.CODEC).networkSynchronized(QuantumChestBlockEntity.Content.STREAM_CODEC));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationBlockEntity>> AUTOMATION_BE = BLOCK_ENTITIES.register("automation",
            () -> new BlockEntityType<>(AutomationBlockEntity::new, AUTOMATION.values().stream().flatMap(List::stream).map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumChestBlockEntity>> QUANTUM_CHEST_BE = BLOCK_ENTITIES.register("quantum_chest",
            () -> new BlockEntityType<>(QuantumChestBlockEntity::new, QUANTUM_CHESTS.stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicDrillerBlockEntity>> BASIC_DRILLER_BE = BLOCK_ENTITIES.register("basic_driller",
            () -> new BlockEntityType<>(BasicDrillerBlockEntity::new, java.util.stream.Stream.concat(MINERS.stream(), PUMPS.stream()).map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdjustableTransformerBlockEntity>> ADJUSTABLE_TRANSFORMER_BE = BLOCK_ENTITIES.register("adjustable_transformer",
            () -> new BlockEntityType<>(AdjustableTransformerBlockEntity::new, ADJUSTABLE_TRANSFORMERS.stream().map(DeferredBlock::get).toArray(Block[]::new)));

    // D133: колодец и машины на мускульной силе
    public static final DeferredBlock<WellBlock> WELL = block("well", WellBlock::new);
    public static final DeferredBlock<MuscleBlock> PRIMITIVE_MINE_DIGGER = block("primitive_mine_digger", p -> new MuscleBlock(true, p));
    public static final DeferredBlock<MuscleBlock> PRIMITIVE_SMALL_ORE_MINER = block("primitive_small_ore_miner", p -> new MuscleBlock(false, p));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WellBlockEntity>> WELL_BE = BLOCK_ENTITIES.register("well",
            () -> new BlockEntityType<>(WellBlockEntity::new, WELL.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MuscleBlockEntity>> MUSCLE_BE = BLOCK_ENTITIES.register("muscle_machine",
            () -> new BlockEntityType<>(MuscleBlockEntity::new, PRIMITIVE_MINE_DIGGER.get(), PRIMITIVE_SMALL_ORE_MINER.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<MuscleMenu>> MUSCLE_MENU = MENUS.register("muscle_machine", () -> IMenuTypeExtension.create(MuscleMenu::client));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MonsterRepellentBlockEntity>> MONSTER_REPELLENT_BE = BLOCK_ENTITIES.register("monster_repellent",
            () -> new BlockEntityType<>(MonsterRepellentBlockEntity::new, MONSTER_REPELLENTS.stream().map(DeferredBlock::get).toArray(Block[]::new)));

    public static final DeferredHolder<MenuType<?>, MenuType<AutomationMenu>> AUTOMATION_MENU = MENUS.register("automation", () -> IMenuTypeExtension.create(AutomationMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<QuantumChestMenu>> QUANTUM_CHEST_MENU = MENUS.register("quantum_chest", () -> IMenuTypeExtension.create(QuantumChestMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<BasicDrillerMenu>> BASIC_DRILLER_MENU = MENUS.register("basic_driller", () -> IMenuTypeExtension.create(BasicDrillerMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<AdjustableTransformerMenu>> ADJUSTABLE_TRANSFORMER_MENU = MENUS.register("adjustable_transformer", () -> IMenuTypeExtension.create(AdjustableTransformerMenu::client));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("automation", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.automation"))
            .icon(() -> new ItemStack(AUTOMATION.get(AutomationType.CHEST_BUFFER).get(0).get()))
            .displayItems((params, out) -> TAB_ORDER.forEach(i -> out.accept(i.get())))
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(AutoContent::registerCapabilities);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MonsterRepellentBlockEntity::onSpawnCheck);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, AUTOMATION_BE.get(), (be, side) -> ru.iis.cover.Covers.items(be, side, be.itemHandler(side)));
        event.registerBlockEntity(Capabilities.Item.BLOCK, QUANTUM_CHEST_BE.get(), QuantumChestBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, BASIC_DRILLER_BE.get(), (be, side) -> ru.iis.cover.Covers.items(be, side, be.itemHandler(side)));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BASIC_DRILLER_BE.get(), (be, side) -> ru.iis.cover.Covers.fluids(be, side, be.fluidHandler(side)));
    }
}
