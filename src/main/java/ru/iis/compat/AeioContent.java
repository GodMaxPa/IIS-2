package ru.iis.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.List;

/**
 * Предметы цепочки AE2 и Ender IO из ИИС 1, которых нет в новых модах (D125): семена и чистые кристаллы (нового AE2 они не знают,
 * рецепты GT_MachineRecipeLoader:395–401, 641–643), плотная стальная пластина для прессов AE2.zs, пластины светокамня и обсидиана.
 */
public final class AeioContent {
    private AeioContent() {}

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    private static final List<DeferredItem<Item>> ORDER = new ArrayList<>();

    private static DeferredItem<Item> item(String id) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(id);
        ORDER.add(i);
        return i;
    }

    public static final DeferredItem<Item> SEED_CERTUS = item("crystal_seed_certus");
    public static final DeferredItem<Item> SEED_NETHER = item("crystal_seed_nether");
    public static final DeferredItem<Item> SEED_FLUIX = item("crystal_seed_fluix");
    public static final DeferredItem<Item> PURE_CERTUS = item("crystal_pure_certus");
    public static final DeferredItem<Item> PURE_NETHER = item("crystal_pure_nether");
    public static final DeferredItem<Item> PURE_FLUIX = item("crystal_pure_fluix");
    public static final DeferredItem<Item> PLATE_DENSE_STEEL = item("plate_dense_steel");
    /** D127: Extra-Cells.zs, корпус ячеек 256K. */
    public static final DeferredItem<Item> CELL_HOUSING_ADVANCED = item("cell_housing_advanced");
    public static final DeferredItem<Item> PLATE_GLOWSTONE = item("plate_glowstone");
    public static final DeferredItem<Item> PLATE_OBSIDIAN = item("plate_obsidian");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("aeio", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.aeio"))
            .icon(() -> new ItemStack(PURE_FLUIX.get()))
            .displayItems((params, out) -> ORDER.forEach(i -> out.accept(i.get())))
            .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
