package ru.iis.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Вкладки творческого режима по назначению (D153, владелец 16.11: «вкладки исправь на логичные категории»).
 * <p>
 * Раньше вкладка была у каждой подсистемы кода (25 штук: «LV», «MV», «снаряжение», «оружие и транспорт», «реактор» …), и одна вещь
 * искалась в трёх местах. Подсистемы по-прежнему сами решают, какие предметы и в каком виде показывать (заряженные батареи, капсулы
 * с жидкостью, семена культур): их вкладки собираются как раньше, но не показываются — всё, что они собрали, раскладывается по
 * одиннадцати вкладкам этого класса. Поиск творческого режима видит предметы по-прежнему.
 */
public final class CreativeTabs {
    private CreativeTabs() {}

    /** Категории в порядке показа. */
    public enum Cat {
        MATERIALS("ore_cassiterite"), PARTS("gear_steel"), MACHINES("macerator_lv"), MULTI("electric_blast_furnace"), PIPES("cable_tin_1x"), TOOLS("wrench_iron"),
        COMBAT("assault_rifle"), TRANSPORT("rocket_t1"), DEFENSE("turret_base_t1"), BIO("colony_core"), MISC("reward_shop");

        final String icon;
        final String id = "cat_" + name().toLowerCase(java.util.Locale.ROOT);

        Cat(String icon) {
            this.icon = icon;
        }
    }

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    /** Что собрали вкладки подсистем: ключ старой вкладки → её предметы в её порядке. */
    private static final Map<String, List<ItemStack>> CAPTURED = new LinkedHashMap<>();
    private static CreativeModeTab.ItemDisplayParameters capturedFor;
    private static boolean capturing;

    private static final String[] RAW = {"dust_", "small_dust_", "tiny_dust_", "impure_dust_", "pure_dust_", "ingot_", "hot_ingot_", "nugget_", "gem_", "block_",
            "crushed_", "purified_", "centrifuged_", "raw_"};
    private static final String[] LINES = {"pipe_", "cable_", "item_pipe_"};
    private static final String[] VEHICLE = {"vehicle_", "chassis_", "engine_", "drivetrain", "wheel_", "body_panels", "dashboard", "landing_gear", "rotor_blades",
            "wing", "fuel_pump", "jerrycan"};

    static {
        for (Cat c : Cat.values()) {
            TABS.register(c.id, () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.iis." + c.id))
                    .icon(() -> icon(c))
                    .displayItems((params, out) -> sorted(params).get(c).forEach(out::accept))
                    .build());
        }
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
        modBus.addListener(EventPriority.LOWEST, CreativeTabs::hideOld);
    }

    private static ItemStack icon(Cat c) {
        Item i = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(IIS.MODID, c.icon));
        return new ItemStack(i == Items.AIR ? Items.IRON_INGOT : i);
    }

    private static boolean old(ResourceKey<CreativeModeTab> key) {
        return key != null && key.identifier().getNamespace().equals(IIS.MODID) && !key.identifier().getPath().startsWith("cat_");
    }

    /** Вкладка подсистемы собрана: запомнить её предметы и убрать их с её страницы (пустая вкладка не показывается). */
    private static void hideOld(BuildCreativeModeTabContentsEvent e) {
        if (!old(e.getTabKey())) return;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack s : e.getParentEntries()) items.add(s.copy());
        CAPTURED.put(e.getTabKey().identifier().getPath(), items);
        e.removeIf(s -> true, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
    }

    /** Предметы всех подсистем по категориям; старые вкладки пересобираются один раз на набор параметров. */
    private static synchronized Map<Cat, List<ItemStack>> sorted(CreativeModeTab.ItemDisplayParameters params) {
        if (capturedFor != params && !capturing) {
            capturing = true;
            try {
                CAPTURED.clear();
                BuiltInRegistries.CREATIVE_MODE_TAB.listElements().forEach(h -> {
                    if (old(h.key())) h.value().buildContents(params);
                });
                capturedFor = params;
            } finally {
                capturing = false;
            }
        }
        Map<Cat, List<ItemStack>> out = new EnumMap<>(Cat.class);
        for (Cat c : Cat.values()) out.put(c, new ArrayList<>());
        CAPTURED.forEach((tab, items) -> {
            for (ItemStack s : items) out.get(category(tab, s)).add(s);
        });
        return out;
    }

    private static boolean starts(String path, String[] prefixes) {
        for (String p : prefixes) if (path.startsWith(p)) return true;
        return false;
    }

    /** Куда идёт предмет: сначала по самому предмету (трубы и провода, сырьё), затем по вкладке подсистемы. */
    static Cat category(String tab, ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        boolean block = stack.getItem() instanceof BlockItem;
        if (path.equals("reward_shop")) return Cat.MISC;
        if (starts(path, LINES) || (path.startsWith("wire_") && !path.startsWith("wire_fine_"))) return Cat.PIPES;
        return switch (tab) {
            case "ores" -> Cat.MATERIALS;
            case "materials" -> starts(path, RAW) ? Cat.MATERIALS : Cat.PARTS;
            case "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv" -> block ? Cat.MACHINES : Cat.PARTS;
            case "machines" -> Cat.MACHINES;
            case "multi", "reactor" -> Cat.MULTI;
            case "fluids" -> Cat.PIPES;
            case "tools", "electric_tools" -> Cat.TOOLS;
            case "gear" -> Cat.COMBAT;
            case "equipment" -> starts(path, VEHICLE) ? Cat.TRANSPORT : Cat.COMBAT;
            case "rail", "space" -> Cat.TRANSPORT;
            case "defense" -> Cat.DEFENSE;
            case "bio" -> Cat.BIO;
            default -> Cat.MISC;   // автоматика и хранилища, грядки, AE и Ender IO
        };
    }
}
