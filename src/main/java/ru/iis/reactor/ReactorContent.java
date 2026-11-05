package ru.iis.reactor;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Ядерный реактор как в IC2 (решение владельца D117): свой код, D5. Стержни — GT5-II; рецепты — scripts/GT_IC2.zs ИИС 1. */
public final class ReactorContent {
    private ReactorContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IIS.MODID);

    public static final ResourceKey<DamageType> RADIATION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(IIS.MODID, "radiation"));
    public static final DeferredHolder<MobEffect, RadiationEffect> RADIATION = EFFECTS.register("radiation", RadiationEffect::new);

    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();

    private static Block.Properties metal(Block.Properties p) {
        return p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Function<Block.Properties, B> f) {
        DeferredBlock<B> b = BLOCKS.registerBlock(id, f, ReactorContent::metal);
        TAB_ORDER.add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    private static DeferredItem<Item> item(String id) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(id);
        TAB_ORDER.add(i);
        return i;
    }

    public static final DeferredBlock<NuclearReactorBlock> NUCLEAR_REACTOR = block("nuclear_reactor", NuclearReactorBlock::new);
    public static final DeferredBlock<Block> REACTOR_CHAMBER = block("reactor_chamber", Block::new);
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_BLOCK = block("solar_panel_block", SolarPanelBlock::new);

    public static final DeferredItem<Item> FUEL_ROD_EMPTY = item("fuel_rod_empty");
    public static final DeferredItem<Item> ENRICHED_URANIUM = item("enriched_uranium");
    public static final DeferredItem<Item> MOX_FUEL = item("mox_fuel");
    public static final DeferredItem<Item> CARBON_MESH = item("carbon_mesh");
    public static final DeferredItem<Item> CARBON_PLATE = item("carbon_plate");
    public static final DeferredItem<Item> PLATE_DENSE_COPPER = item("plate_dense_copper");
    /** Солнечные панели-крышки: GT_MetaGenerated_Item_01:785–794. */
    public static final List<DeferredItem<Item>> SOLAR_PANELS = java.util.stream.Stream.of("solar_panel", "solar_panel_8v", "solar_panel_lv", "solar_panel_mv", "solar_panel_hv", "solar_panel_ev", "solar_panel_iv",
            "solar_panel_luv", "solar_panel_zpm", "solar_panel_uv").map(ReactorContent::item).toList();

    /** Детали реактора, по списку {@link ReactorCore#PARTS}: прочность — ёмкость детали. */
    public static final Map<String, DeferredItem<ReactorPartItem>> PARTS;
    static {
        var m = new java.util.HashMap<String, DeferredItem<ReactorPartItem>>();
        ReactorCore.PARTS.keySet().stream().sorted().forEach(id -> {
            ReactorCore.Part part = ReactorCore.PARTS.get(id);
            DeferredItem<ReactorPartItem> i = ITEMS.registerItem(id, p -> new ReactorPartItem(part, part.capacity() > 0 ? p.durability(part.capacity()) : p.stacksTo(1)));
            m.put(id, i);
            TAB_ORDER.add(i);
        });
        PARTS = Map.copyOf(m);
    }
    /** Отработанные стержни. */
    public static final List<DeferredItem<Item>> DEPLETED;
    static {
        var l = new ArrayList<DeferredItem<Item>>();
        ReactorCore.PARTS.values().stream().map(ReactorCore.Part::replacedBy).filter(s -> s != null).distinct().sorted().forEach(id -> l.add(item(id)));
        DEPLETED = List.copyOf(l);
    }

    // --- защитный костюм: снимает радиацию, если надет целиком (IC2 ItemArmorHazmat) -----------------------------------------
    private static final ResourceKey<EquipmentAsset> HAZMAT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(IIS.MODID, "hazmat"));
    private static final ArmorMaterial HAZMAT = new ArmorMaterial(64, Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 2, ArmorType.CHESTPLATE, 2, ArmorType.HELMET, 1, ArmorType.BODY, 2),
            1, SoundEvents.ARMOR_EQUIP_LEATHER, 0F, 0F, ItemTags.REPAIRS_LEATHER_ARMOR, HAZMAT_ASSET);

    private static DeferredItem<Item> armor(String id, ArmorType type) {
        DeferredItem<Item> i = ITEMS.registerItem(id, p -> new Item(p.humanoidArmor(HAZMAT, type)));
        TAB_ORDER.add(i);
        return i;
    }

    public static final DeferredItem<Item> HAZMAT_HELMET = armor("hazmat_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> HAZMAT_CHESTPLATE = armor("hazmat_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> HAZMAT_LEGGINGS = armor("hazmat_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> RUBBER_BOOTS = armor("rubber_boots", ArmorType.BOOTS);

    public static boolean fullHazmat(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.HEAD).is(HAZMAT_HELMET.get()) && e.getItemBySlot(EquipmentSlot.CHEST).is(HAZMAT_CHESTPLATE.get())
                && e.getItemBySlot(EquipmentSlot.LEGS).is(HAZMAT_LEGGINGS.get()) && e.getItemBySlot(EquipmentSlot.FEET).is(RUBBER_BOOTS.get());
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NuclearReactorBlockEntity>> REACTOR_BE = BLOCK_ENTITIES.register("nuclear_reactor",
            () -> new BlockEntityType<>(NuclearReactorBlockEntity::new, NUCLEAR_REACTOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_BE = BLOCK_ENTITIES.register("solar_panel_block",
            () -> new BlockEntityType<>(SolarPanelBlockEntity::new, SOLAR_PANEL_BLOCK.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<ReactorMenu>> REACTOR_MENU = MENUS.register("nuclear_reactor",
            () -> new MenuType<>(ReactorMenu::client, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("reactor", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.reactor"))
            .icon(() -> new ItemStack(NUCLEAR_REACTOR.get()))
            .displayItems((params, out) -> TAB_ORDER.forEach(i -> out.accept(i.get())))
            .build());

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, REACTOR_BE.get(), (be, side) -> ru.iis.cover.Covers.items(be, side, be.itemHandler(side)));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(ReactorContent::capabilities);
    }
}
