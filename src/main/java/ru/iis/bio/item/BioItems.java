package ru.iis.bio.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
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
 * Предметы Био первого среза Б-1 (спецификация v0.7 §22.4, D70): массы, волокна, костные детали, гель, каркас, подложка,
 * ткани, посевы, остатки, туши, инструменты. Рецепты — `iis/bio_recipes.json` (tools/gen/bio.py).
 */
public final class BioItems {
    private BioItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IIS.MODID);

    /** Что ещё лежит в туше помимо мяса, жил и костей (кожа, шерсть, перо) — записывается при разделке убитого животного. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ItemStack>>> CARCASS_EXTRAS = COMPONENTS.registerComponentType("carcass_extras",
            b -> b.persistent(ItemStack.CODEC.listOf()).networkSynchronized(ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list())));
    /** Линия колонии, из которой взят посев или выращен орган (зачаток генетики: пока только происхождение). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> LINEAGE = COMPONENTS.registerComponentType("bio_lineage",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** Содержимое мерного ковша. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LadleItem.Content>> LADLE = COMPONENTS.registerComponentType("ladle",
            b -> b.persistent(LadleItem.Content.CODEC).networkSynchronized(LadleItem.Content.STREAM_CODEC));

    public static final List<DeferredItem<? extends Item>> TAB = new ArrayList<>();

    private static DeferredItem<Item> simple(String id) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(id);
        TAB.add(i);
        return i;
    }

    // --- инструменты (неживые) ----------------------------------------------------------------------------------
    /** Каменный разделочный нож (W02): 64 операции; убийство взрослого животного этим ножом даёт тушу вместо обычной добычи. */
    public static final DeferredItem<Item> KNIFE = reg("bio_knife", p -> new BioKnifeItem(p.durability(64)));
    /** Плетёная оправка (W07): 20 формовок. */
    public static final DeferredItem<Item> MANDREL = reg("woven_mandrel", p -> new Item(p.durability(20)));
    /** Мерный ковш: переносит жидкости порциями 50 / 250 / 1000 мБ. */
    public static final DeferredItem<Item> LADLE_ITEM = reg("measuring_ladle", p -> new LadleItem(p.stacksTo(1)));
    /** Грубая фильтровальная прокладка W09: сменная, 25 партий фильтра (D72). */
    public static final DeferredItem<Item> FILTER_PAD = reg("filter_pad", p -> new Item(p.durability(25)));

    private static DeferredItem<Item> reg(String id, java.util.function.Function<Item.Properties, Item> f) {
        DeferredItem<Item> i = ITEMS.registerItem(id, f);
        TAB.add(i);
        return i;
    }

    // --- туши -------------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> CARCASS_SMALL = reg("carcass_small", p -> new CarcassItem(p.stacksTo(16)));
    public static final DeferredItem<Item> CARCASS_MEDIUM = reg("carcass_medium", p -> new CarcassItem(p.stacksTo(8)));
    public static final DeferredItem<Item> CARCASS_LARGE = reg("carcass_large", p -> new CarcassItem(p.stacksTo(4)));
    public static final DeferredItem<Item> CARCASS_ROTTEN = reg("carcass_rotten", p -> new CarcassItem(p.stacksTo(8)));

    // --- сырьё и полуфабрикаты ----------------------------------------------------------------------------------------
    public static final DeferredItem<Item> MEAT_MASS = simple("meat_mass");
    public static final DeferredItem<Item> PLANT_MASS = simple("plant_mass");
    public static final DeferredItem<Item> ROTTEN_MASS = simple("rotten_mass");
    public static final DeferredItem<Item> PLANT_FIBER = simple("plant_fiber");
    public static final DeferredItem<Item> CONNECTIVE_FIBER = simple("connective_fiber");
    public static final DeferredItem<Item> SINEW_CORD = simple("sinew_cord");
    public static final DeferredItem<Item> GEL = simple("binding_gel");
    public static final DeferredItem<Item> BONE_FRAGMENT = simple("bone_fragment");
    public static final DeferredItem<Item> BONE_RIB = simple("bone_rib");
    public static final DeferredItem<Item> BONE_POWDER = simple("bone_powder");
    public static final DeferredItem<Item> WOVEN_FRAME = simple("woven_frame");
    public static final DeferredItem<Item> GROWTH_SUBSTRATE = simple("growth_substrate");
    public static final DeferredItem<Item> ORGANIC_RESIDUE = simple("organic_residue");
    public static final DeferredItem<Item> PLANT_RESIDUE = simple("plant_residue");
    public static final DeferredItem<Item> ENERGY_PASTE = simple("energy_paste");
    /** Костяное кольцо B18: ручная формовка на оправке (D72). */
    public static final DeferredItem<Item> BONE_RING = simple("bone_ring");

    // --- живое ------------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> SOFT_TISSUE = reg("soft_tissue", p -> new LivingItem(p));
    public static final DeferredItem<Item> LIVING_MEMBRANE = reg("living_membrane", p -> new LivingItem(p));
    /** Волокнистая ткань B15 и выстилка B19 — стенки сосудов LV (переходное выращивание на ULV-ложе, D72). */
    public static final DeferredItem<Item> FIBROUS_TISSUE = reg("fibrous_tissue", p -> new LivingItem(p));
    public static final DeferredItem<Item> TISSUE_LINING = reg("tissue_lining", p -> new LivingItem(p));
    /** Неразвитая культура: соскоб живой ткани колонии ножом (D70: на челноке развитого посева нет). */
    public static final DeferredItem<Item> PRIMITIVE_CULTURE = reg("primitive_culture", p -> new LivingItem(p));
    /** Базовый посев колонии: почка ядра; нужен для каждого нового органа. */
    public static final DeferredItem<Item> BASE_SEED = reg("base_seed", p -> new LivingItem(p));
    /** Мышечный посев — временный рецепт почкования (в спецификации v0.7 не задан, D73): начало мышечной линии B16. */
    public static final DeferredItem<Item> MUSCLE_SEED = reg("muscle_seed", p -> new LivingItem(p));
    /** Мышечный пучок B16 и мышечный узел B17 — привод насоса, гнезда, желёз (§22.4). */
    public static final DeferredItem<Item> MUSCLE_BUNDLE = reg("muscle_bundle", p -> new LivingItem(p));
    public static final DeferredItem<Item> MUSCLE_NODE = reg("muscle_node", p -> new LivingItem(p));
    /** Клапанный лепесток B20: обратный клапан и насос. */
    public static final DeferredItem<Item> VALVE_PETAL = reg("valve_petal", p -> new LivingItem(p));
    /** Фильтровальная прокладка B27: 100 партий мембранного фильтра F09 (D73). */
    public static final DeferredItem<Item> FINE_FILTER_PAD = reg("fine_filter_pad", p -> new Item(p.durability(100)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
    }
}
