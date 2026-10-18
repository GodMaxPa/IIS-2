package ru.iis.core.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.core.material.IISMaterial;
import ru.iis.core.material.MaterialData;

import java.util.*;

/** Регистрация предметов-форм материалов из /iis/items.json. */
public final class MaterialItems {
    private MaterialItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);

    private static final List<DeferredItem<MaterialItem>> ALL = new ArrayList<>();

    static {
        Map<String, IISMaterial> mats = new HashMap<>();
        MaterialData.materials().forEach(m -> mats.put(m.id(), m));
        for (MaterialData.FormItem f : MaterialData.items()) {
            IISMaterial m = Objects.requireNonNull(mats.get(f.material()), f.material());
            ALL.add(ITEMS.registerItem(f.id(), p -> new MaterialItem(p, f.prefix(), m)));
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("materials", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.materials"))
            .icon(() -> new ItemStack(item("ingot/bronze")))
            .displayItems((params, out) -> ALL.forEach(i -> out.accept(i.get())))
            .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** Предмет формы, например item("crushed/chalcopyrite") или item("dust/redstone") (ванильный). */
    public static Item item(String formAndMaterial) {
        String id = MaterialData.forms().get(formAndMaterial);
        if (id == null) throw new IllegalArgumentException("No item for " + formAndMaterial);
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    public static Optional<Item> find(String formAndMaterial) {
        String id = MaterialData.forms().get(formAndMaterial);
        return id == null ? Optional.empty() : Optional.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }
}
