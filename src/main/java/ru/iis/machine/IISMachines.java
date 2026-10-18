package ru.iis.machine;

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
import ru.iis.machine.block.*;
import ru.iis.machine.menu.BbfMenu;
import ru.iis.machine.menu.BoilerMenu;
import ru.iis.machine.menu.SteamMachineMenu;

import java.util.*;
import java.util.function.Supplier;

/**
 * Регистрация машин эпохи пара: блоки, предметы, блок-сущности, меню, вкладка творческого режима, capability.
 * Модели, переводы, лут и рецепты генерирует tools/gen/steam.py.
 */
public final class IISMachines {
    private IISMachines() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);

    /** Порядок в творческой вкладке. */
    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();

    private static Block.Properties metal(Block.Properties p) {
        // GT5 машины: прочность 10 (как железный блок ×2), добываются киркой
        return p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, java.util.function.Function<Block.Properties, B> f, java.util.function.UnaryOperator<Block.Properties> props) {
        DeferredBlock<B> b = BLOCKS.registerBlock(id, f, props);
        TAB_ORDER.add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    // --- простые блоки ------------------------------------------------------------------------------------------
    public static final Map<String, DeferredBlock<Block>> HULLS = new LinkedHashMap<>();
    static {
        for (String id : List.of("hull_bronze", "hull_bronze_bricked", "hull_steel", "hull_steel_bricked")) {
            HULLS.put(id, block(id, Block::new, IISMachines::metal));
        }
    }
    public static final DeferredBlock<Block> FIREBRICK_CASING = block("firebrick_casing", Block::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).sound(SoundType.STONE).strength(2.0f, 6.0f).requiresCorrectToolForDrops());

    // --- котлы ----------------------------------------------------------------------------------------------
    public static final Map<BoilerType, DeferredBlock<BoilerBlock>> BOILERS = new EnumMap<>(BoilerType.class);
    static {
        for (BoilerType t : BoilerType.values()) {
            BOILERS.put(t, block(t.id, p -> new BoilerBlock(t, p), p -> metal(p).lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 13 : 0)));
        }
    }

    // --- паровые машины ------------------------------------------------------------------------------------------
    public static final Map<String, DeferredBlock<SteamMachineBlock>> STEAM_MACHINES = new LinkedHashMap<>();
    static {
        for (SteamMachineType t : SteamMachineType.values()) {
            for (SteamMachineType.Tier tier : SteamMachineType.Tier.values()) {
                String id = t.blockId(tier);
                STEAM_MACHINES.put(id, block(id, p -> new SteamMachineBlock(t, tier, p), IISMachines::metal));
            }
        }
    }

    public static final DeferredBlock<BbfBlock> BRICKED_BLAST_FURNACE = block("bricked_blast_furnace", BbfBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).sound(SoundType.STONE).strength(2.0f, 6.0f).requiresCorrectToolForDrops()
                    .lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 13 : 0));

    // --- трубы ------------------------------------------------------------------------------------------------
    public static final Map<String, DeferredBlock<PipeBlock>> PIPES = new LinkedHashMap<>();
    static {
        for (PipeBlock.Spec s : PipeBlock.SPECS) {
            PIPES.put(s.id(), block(s.id(), p -> new PipeBlock(s, p),
                    p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion()));
        }
    }

    // --- предметы ------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> COMPRESSED_FIRECLAY = ITEMS.registerSimpleItem("compressed_fireclay");
    public static final DeferredItem<Item> FIREBRICK = ITEMS.registerSimpleItem("firebrick");
    static {
        TAB_ORDER.add(COMPRESSED_FIRECLAY);
        TAB_ORDER.add(FIREBRICK);
    }

    // --- блок-сущности ---------------------------------------------------------------------------------------
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>> BOILER_BE = BLOCK_ENTITIES.register("boiler",
            () -> new BlockEntityType<>(BoilerBlockEntity::new, BOILERS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamMachineBlockEntity>> STEAM_MACHINE_BE = BLOCK_ENTITIES.register("steam_machine",
            () -> new BlockEntityType<>(SteamMachineBlockEntity::new, STEAM_MACHINES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BbfBlockEntity>> BBF_BE = BLOCK_ENTITIES.register("bricked_blast_furnace",
            () -> new BlockEntityType<>(BbfBlockEntity::new, BRICKED_BLAST_FURNACE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PipeBlockEntity>> PIPE_BE = BLOCK_ENTITIES.register("fluid_pipe",
            () -> new BlockEntityType<>(PipeBlockEntity::new, PIPES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));

    // --- меню (клиент получает позицию блока и по ней узнаёт вид машины) ----------------------------------------------
    public static final DeferredHolder<MenuType<?>, MenuType<BoilerMenu>> BOILER_MENU = MENUS.register("boiler",
            () -> IMenuTypeExtension.create(BoilerMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<SteamMachineMenu>> STEAM_MACHINE_MENU = MENUS.register("steam_machine",
            () -> IMenuTypeExtension.create(SteamMachineMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<BbfMenu>> BBF_MENU = MENUS.register("bricked_blast_furnace",
            () -> IMenuTypeExtension.create(BbfMenu::client));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("machines", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.machines"))
            .icon(() -> new ItemStack(BOILERS.get(BoilerType.COAL_BRONZE).get()))
            .displayItems((params, out) -> TAB_ORDER.forEach(i -> out.accept(i.get())))
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(IISMachines::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BOILER_BE.get(), BoilerBlockEntity::fluidHandler);
        // GT5 (IIS AllowSmallBoilerAutomation=false): предметы в котёл автоматикой не подаются — item capability нет
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, STEAM_MACHINE_BE.get(), SteamMachineBlockEntity::fluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, STEAM_MACHINE_BE.get(), SteamMachineBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, BBF_BE.get(), BbfBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, PIPE_BE.get(), PipeBlockEntity::fluidHandler);
    }
}
