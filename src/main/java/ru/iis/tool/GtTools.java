package ru.iis.tool;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.core.material.MaterialData;

import java.util.ArrayList;
import java.util.List;

/** Регистрация инструментов GT из /iis/tools.json. */
public final class GtTools {
    private GtTools() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    private static final List<DeferredItem<GtToolItem>> ALL = new ArrayList<>();

    static {
        for (MaterialData.ToolDef d : MaterialData.tools()) {
            ALL.add(ITEMS.registerItem(d.id(), p -> new GtToolItem(properties(p, d), d)));
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("tools", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.tools"))
            .icon(() -> new ItemStack(ALL.stream().filter(t -> t.getId().getPath().equals("hammer_bronze")).findFirst().orElse(ALL.getFirst()).get()))
            .displayItems((params, out) -> ALL.forEach(t -> out.accept(t.get())))
            .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** Уровень GT5 (0..) -> блоки, с которых инструмент НЕ получает дроп. */
    static TagKey<Block> incorrectFor(int quality) {
        return switch (quality) {
            case 0 -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
            case 1 -> BlockTags.INCORRECT_FOR_STONE_TOOL;
            case 2 -> BlockTags.INCORRECT_FOR_IRON_TOOL;
            case 3 -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            default -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        };
    }

    static Item.Properties properties(Item.Properties p, MaterialData.ToolDef d) {
        TagKey<Item> repair = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/" + d.material()));
        ToolMaterial mat = new ToolMaterial(incorrectFor(d.quality()), Math.max(1, d.maxDamage()), d.speed(),
                Math.min(4, d.quality()), 10, repair);
        if (d.kind() == null) return p.durability(Math.max(1, d.maxDamage()));
        Item.Properties out = switch (d.kind()) {
            case "pickaxe" -> p.pickaxe(mat, 1.0f, -2.8f);
            case "axe" -> p.axe(mat, 5.0f, -3.0f);
            case "shovel" -> p.shovel(mat, 1.5f, -3.0f);
            case "hoe" -> p.hoe(mat, -1.0f, -1.0f);
            case "sword" -> d.tool().equals("knife") ? p.sword(mat, 1.0f, -2.0f) : p.sword(mat, 3.0f, -2.4f);
            default -> throw new IllegalArgumentException("Unknown tool kind " + d.kind());
        };
        return out;
    }
}
