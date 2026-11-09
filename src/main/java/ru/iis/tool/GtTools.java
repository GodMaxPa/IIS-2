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
import java.util.Set;

/** Регистрация инструментов GT из /iis/tools.json. */
public final class GtTools {
    private GtTools() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    private static final List<DeferredItem<GtToolItem>> ALL = new ArrayList<>();
    /** Электроинструменты LV (D78): регистрирует ru.iis.lv.LvContent, у них заряд вместо прочности. */
    public static final Set<String> ELECTRIC = Set.of("drill_lv", "chainsaw_lv", "wrench_lv",
            // D144: электроинструменты GT MV/HV, отбойный молоток, циркулярная пила, шуруповёрт
            "drill_mv", "drill_hv", "chainsaw_mv", "chainsaw_hv", "wrench_mv", "wrench_hv", "jackhammer", "buzzsaw", "screwdriver_lv");

    /** Уровень напряжения электроинструмента GT: LV 1, MV 2, HV 3 (отбойный молоток — HV). */
    public static int electricTier(String tool) {
        if (tool.endsWith("_mv")) return 2;
        if (tool.endsWith("_hv") || tool.equals("jackhammer")) return 3;
        return 1;
    }
    /** GT_Tool_Drill_LV#isMinableBlock: всё, что берут кирка и лопата. */
    public static final TagKey<Block> MINEABLE_DRILL = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(IIS.MODID, "mineable/drill"));

    static {
        for (MaterialData.ToolDef d : MaterialData.tools()) {
            if (ELECTRIC.contains(d.tool())) continue;
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

    public static Item.Properties properties(Item.Properties p, MaterialData.ToolDef d) {
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
            // GT5: машины, корпуса, люки и трубы снимаются только ключом; провода — кусачками
            case "wrench" -> p.tool(mat, TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(IIS.MODID, "mineable/wrench")), 1.0f, -2.8f, 0f);
            case "wire_cutter" -> p.tool(mat, TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(IIS.MODID, "mineable/wire_cutter")), 1.0f, -2.8f, 0f);
            // GT_Tool_Drill_LV (урон 2), GT_Tool_Chainsaw_LV (урон 3, пила по дереву)
            case "drill" -> p.tool(mat, MINEABLE_DRILL, 2.0f, -2.8f, 0f);
            case "chainsaw" -> p.tool(mat, BlockTags.MINEABLE_WITH_AXE, 3.0f, -3.0f, 0f);
            // D144: GT_Tool_Sense (урон 3, косит растения вокруг), GT_Tool_Plow (пашет 3×3), GT_Tool_BranchCutter (листва -> саженцы)
            case "sense" -> p.tool(mat, BlockTags.MINEABLE_WITH_HOE, 3.0f, -3.0f, 0f);
            case "plow" -> p.tool(mat, BlockTags.MINEABLE_WITH_SHOVEL, 1.0f, -3.0f, 0f);
            case "branch_cutter" -> p.tool(mat, BlockTags.LEAVES, 2.5f, -3.0f, 0f);
            default -> throw new IllegalArgumentException("Unknown tool kind " + d.kind());
        };
        return out;
    }
}
