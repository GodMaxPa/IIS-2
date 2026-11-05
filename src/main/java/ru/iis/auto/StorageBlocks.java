package ru.iis.auto;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Блоки металлов и самоцветов GT5-II (GT_Block_Metal, D108): список — материалы ИИС 2, у которых в GT есть блок хранения
 * (iis/storage_blocks.json, генератор tools/gen/auto.py); тег c:storage_blocks/&lt;материал&gt;.
 */
public final class StorageBlocks {
    private StorageBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final Map<String, DeferredBlock<Block>> ALL = new LinkedHashMap<>();

    static {
        try (InputStream in = StorageBlocks.class.getResourceAsStream("/iis/storage_blocks.json")) {
            if (in != null) {
                for (JsonElement e : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray()) {
                    String mat = e.getAsString(), id = "block_" + mat;
                    DeferredBlock<Block> b = BLOCKS.registerSimpleBlock(id, p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops());
                    ITEMS.registerSimpleBlockItem(id, b);
                    ALL.put(mat, b);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot read iis/storage_blocks.json", e);
        }
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
