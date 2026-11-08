package ru.iis.world;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.level.block.CreateFluidSourceEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

import java.util.List;

/**
 * Начало игры по правкам владельца 08.11 (D142): жидкости не бесконечные (два источника не дают третий), каменные инструменты слабее,
 * кремниевые инструменты на уровне ванильных каменных (в ИИС 1 — инструменты GT из кремня). Числа каменных — предложение ИИ.
 */
public final class EarlyGame {
    private EarlyGame() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final TagKey<Item> FLINT_REPAIR = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IIS.MODID, "flint_tool_materials"));
    /** Кремень — как ванильный камень: 131 прочности, скорость 4, +1 урона, добывает то же. */
    public static final ToolMaterial FLINT = new ToolMaterial(BlockTags.INCORRECT_FOR_STONE_TOOL, 131, 4.0F, 1.0F, 5, FLINT_REPAIR);
    /** Каменные инструменты: прочность вдвое меньше (131 → 66), скорость 4 → 3. */
    public static final int STONE_DURABILITY = 66;
    public static final float STONE_SPEED = 3.0F;

    public static final DeferredItem<Item> FLINT_SWORD = ITEMS.registerItem("flint_sword", p -> new Item(p.sword(FLINT, 3.0F, -2.4F)));
    public static final DeferredItem<ShovelItem> FLINT_SHOVEL = ITEMS.registerItem("flint_shovel", p -> new ShovelItem(FLINT, 1.5F, -3.0F, p));
    public static final DeferredItem<Item> FLINT_PICKAXE = ITEMS.registerItem("flint_pickaxe", p -> new Item(p.pickaxe(FLINT, 1.0F, -2.8F)));
    public static final DeferredItem<AxeItem> FLINT_AXE = ITEMS.registerItem("flint_axe", p -> new AxeItem(FLINT, 7.0F, -3.2F, p));
    public static final DeferredItem<HoeItem> FLINT_HOE = ITEMS.registerItem("flint_hoe", p -> new HoeItem(FLINT, -1.0F, -2.0F, p));

    public static List<DeferredItem<? extends Item>> flintTools() {
        return List.of(FLINT_SWORD, FLINT_SHOVEL, FLINT_PICKAXE, FLINT_AXE, FLINT_HOE);
    }

    private static void fluids(CreateFluidSourceEvent event) {
        event.setCanConvert(false);
    }

    private static void weakenStone(ModifyDefaultComponentsEvent event) {
        record T(Item item, TagKey<Block> mines) {}
        for (T t : List.of(new T(Items.STONE_PICKAXE, BlockTags.MINEABLE_WITH_PICKAXE), new T(Items.STONE_AXE, BlockTags.MINEABLE_WITH_AXE),
                new T(Items.STONE_SHOVEL, BlockTags.MINEABLE_WITH_SHOVEL), new T(Items.STONE_HOE, BlockTags.MINEABLE_WITH_HOE))) {
            event.modify(t.item(), (b, registries, item) -> {
                HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
                b.set(DataComponents.MAX_DAMAGE, STONE_DURABILITY).set(DataComponents.TOOL, new Tool(List.of(Tool.Rule.deniesDrops(blocks.getOrThrow(BlockTags.INCORRECT_FOR_STONE_TOOL)),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(t.mines()), STONE_SPEED)), 1.0F, 1, true));
            });
        }
        event.modify(Items.STONE_SWORD, b -> b.set(DataComponents.MAX_DAMAGE, STONE_DURABILITY));
        event.modify(Items.STONE_SPEAR, b -> b.set(DataComponents.MAX_DAMAGE, STONE_DURABILITY));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(EarlyGame::weakenStone);
        NeoForge.EVENT_BUS.addListener(EarlyGame::fluids);
        modBus.addListener(EarlyGame::tabs);
    }

    private static void tabs(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES || event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.COMBAT) {
            flintTools().forEach(i -> event.accept(i.get()));
        }
    }
}
