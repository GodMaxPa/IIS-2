package ru.iis.monitor;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

import java.util.List;

/** Панели IC2 Nuclear Control из книги квестов ИИС 1 (D141). */
public final class MonitorContent {
    private MonitorContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);

    public static final DeferredItem<Item> COMPUTER_MONITOR = ITEMS.registerSimpleItem("computer_monitor");
    public static final DeferredItem<SensorCardItem> SENSOR_KIT = ITEMS.registerItem("gt_sensor_kit", p -> new SensorCardItem(true, p));
    public static final DeferredItem<SensorCardItem> SENSOR_CARD = ITEMS.registerItem("gt_sensor_card", p -> new SensorCardItem(false, p.stacksTo(1)));
    public static final DeferredItem<Item> FREQUENCY_TRANSMITTER = ITEMS.registerSimpleItem("frequency_transmitter");
    public static final DeferredItem<Item> RANGE_UPGRADE = ITEMS.registerSimpleItem("range_upgrade");
    public static final DeferredItem<Item> COLOR_UPGRADE = ITEMS.registerSimpleItem("color_upgrade");

    public static final DeferredBlock<PanelBlock> PANEL = panel("information_panel", false, false);
    public static final DeferredBlock<PanelBlock> EXTENDER = panel("information_panel_extender", false, true);
    public static final DeferredBlock<PanelBlock> ADVANCED_PANEL = panel("advanced_information_panel", true, false);
    public static final DeferredBlock<PanelBlock> ADVANCED_EXTENDER = panel("advanced_panel_extender", true, true);
    public static final List<DeferredBlock<PanelBlock>> ALL_PANELS = List.of(PANEL, EXTENDER, ADVANCED_PANEL, ADVANCED_EXTENDER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PanelBlockEntity>> PANEL_BE = BLOCK_ENTITIES.register("information_panel",
            () -> new BlockEntityType<>(PanelBlockEntity::new, PANEL.get(), ADVANCED_PANEL.get()));

    private static DeferredBlock<PanelBlock> panel(String id, boolean advanced, boolean extender) {
        DeferredBlock<PanelBlock> b = BLOCKS.registerBlock(id, p -> new PanelBlock(advanced, extender, p),
                p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops());
        ITEMS.registerSimpleBlockItem(id, b);
        return b;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
