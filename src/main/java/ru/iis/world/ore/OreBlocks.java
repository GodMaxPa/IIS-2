package ru.iis.world.ore;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.core.material.IISMaterial;
import ru.iis.core.material.MaterialData;

import java.util.*;

/**
 * Блоки руд GT: по одному на пару (материал, порода).
 * Модели, переводы, лут и теги инструментов генерирует tools/gen/gen_ores.py.
 */
public final class OreBlocks {
    private OreBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);

    /** materialId -> порода -> блок */
    private static final Map<String, EnumMap<OreHost, DeferredBlock<Block>>> ORES = new LinkedHashMap<>();
    private static final Map<String, IISMaterial> MATERIALS = new HashMap<>();

    static {
        for (IISMaterial m : MaterialData.materials()) {
            MATERIALS.put(m.id(), m);
            EnumMap<OreHost, DeferredBlock<Block>> byHost = new EnumMap<>(OreHost.class);
            for (String hostId : m.hosts()) {
                OreHost host = OreHost.byId(hostId);
                // GT5: прочность руды = 1 + уровень добычи; глубинный сланец твёрже, как в ванили
                float hardness = 1.0f + m.harvestLevel() + host.extraHardness();
                String id = host.blockId(m.id());
                DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(id, p -> p
                        .mapColor(host.mapColor()).sound(host.sound())
                        .strength(hardness, hardness).requiresCorrectToolForDrops());
                ITEMS.registerSimpleBlockItem(id, block);
                byHost.put(host, block);
            }
            ORES.put(m.id(), byHost);
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("ores", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.ores"))
            .icon(() -> new ItemStack(get("chalcopyrite", OreHost.STONE).getBlock().asItem()))
            .displayItems((params, out) -> ORES.values().forEach(m -> m.values().forEach(b -> out.accept(b.get()))))
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** Состояние блока руды или null, если для этой породы руды такого материала нет. */
    public static BlockState get(String materialId, OreHost host) {
        EnumMap<OreHost, DeferredBlock<Block>> byHost = ORES.get(materialId);
        if (byHost == null) return null;
        DeferredBlock<Block> b = byHost.get(host);
        return b == null ? null : b.get().defaultBlockState();
    }

    public static Map<String, EnumMap<OreHost, DeferredBlock<Block>>> all() {
        return Collections.unmodifiableMap(ORES);
    }

    public static IISMaterial material(String id) {
        return MATERIALS.get(id);
    }
}
