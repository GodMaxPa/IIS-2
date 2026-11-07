package ru.iis.equipment;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.equipment.vehicle.Driveable;
import ru.iis.equipment.vehicle.VehicleItem;
import ru.iis.equipment.vehicle.VehicleKind;
import ru.iis.equipment.vehicle.VehicleMenu;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Оружие, броня и транспорт по эпохам Кибера (D128). Модели, переводы и рецепты — tools/gen/equipment.py. */
public final class EquipmentContent {
    private EquipmentContent() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(IIS.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IIS.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);

    public static final ResourceKey<DamageType> DAMAGE_BULLET = damage("bullet"), DAMAGE_LASER = damage("laser"), DAMAGE_PLASMA = damage("plasma"), DAMAGE_BLAST = damage("shell_blast");

    private static ResourceKey<DamageType> damage(String id) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(IIS.MODID, id));
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GUN_AMMO = COMPONENTS.registerComponentType("gun_ammo",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GUN_HAS_MAG = COMPONENTS.registerComponentType("gun_has_mag",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ru.iis.fluid.FluidContent>> GUN_PLASMA = COMPONENTS.registerComponentType("gun_plasma",
            b -> b.persistent(ru.iis.fluid.FluidContent.CODEC).networkSynchronized(ru.iis.fluid.FluidContent.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> GUN_RELOAD = COMPONENTS.registerComponentType("gun_reload",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> VEHICLE_FUEL = COMPONENTS.registerComponentType("vehicle_fuel",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> VEHICLE_DAMAGE = COMPONENTS.registerComponentType("vehicle_damage",
            b -> b.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> VEHICLE_KIT = COMPONENTS.registerComponentType("vehicle_kit",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();
    public static final Map<Weapon, DeferredItem<? extends Item>> GUNS = new EnumMap<>(Weapon.class);
    public static final Map<Weapon.Ammo, DeferredItem<Item>> AMMO = new EnumMap<>(Weapon.Ammo.class);
    public static final Map<Weapon.Magazine, DeferredItem<MagazineItem>> MAGAZINES = new EnumMap<>(Weapon.Magazine.class);
    public static final Map<VehicleKind, DeferredItem<VehicleItem>> VEHICLES = new EnumMap<>(VehicleKind.class);
    public static final Map<VehicleKind, DeferredHolder<EntityType<?>, EntityType<Driveable>>> VEHICLE_TYPES = new EnumMap<>(VehicleKind.class);

    private static DeferredItem<Item> simple(String id) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(id);
        TAB_ORDER.add(i);
        return i;
    }

    private static ArmorMaterial material(String asset, int durability, int boots, int legs, int chest, int helmet, int enchant, float toughness, float knockback) {
        ResourceKey<EquipmentAsset> key = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(IIS.MODID, asset));
        return new ArmorMaterial(durability, Map.of(ArmorType.BOOTS, boots, ArmorType.LEGGINGS, legs, ArmorType.CHESTPLATE, chest, ArmorType.HELMET, helmet, ArmorType.BODY, chest),
                enchant, SoundEvents.ARMOR_EQUIP_IRON, toughness, knockback, ItemTags.REPAIRS_IRON_ARMOR, key);
    }

    // пар и LV: как железо, но прочнее; MV и HV: как алмаз; EV: как незерит (числа брони — предложение D128)
    private static final ArmorMaterial MUSKETEER = material("musketeer", 20, 2, 5, 6, 2, 9, 0F, 0F);
    private static final ArmorMaterial BALLISTIC = material("ballistic", 33, 3, 6, 8, 3, 10, 2F, 0F);
    private static final ArmorMaterial ASSAULT = material("assault", 42, 3, 6, 8, 3, 10, 3F, 0.1F);
    private static final ArmorMaterial POWERED = material("power_armor", 1, 0, 0, 0, 0, 1, 0F, 0F);
    private static final ArmorMaterial PLASMA = material("plasma_armor", 1, 0, 0, 0, 0, 1, 0F, 0F);

    private static void armorSet(String set, ArmorMaterial m) {
        for (ArmorType t : List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS)) {
            String id = set + "_" + t.getName();
            TAB_ORDER.add(ITEMS.registerItem(id, p -> new Item(p.humanoidArmor(m, t))));
        }
    }

    private static void poweredSet(String set, ArmorMaterial m, long capacity, long transfer, int tier, long euPerDamage, boolean plasma) {
        for (ArmorType t : List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS)) {
            String id = set + "_" + t.getName();
            TAB_ORDER.add(ITEMS.registerItem(id, p -> new PoweredArmorItem(capacity, transfer, tier, 1.0, euPerDamage, plasma,
                    p.stacksTo(1).humanoidArmor(m, t).component(DataComponents.UNBREAKABLE, Unit.INSTANCE))));
        }
    }

    static {
        for (Weapon w : Weapon.values()) {
            UseEffects use = new UseEffects(true, false, w.walkSpeed);
            DeferredItem<? extends Item> item = w.electric()
                    ? ITEMS.registerItem(w.id, p -> new EnergyGunItem(w, p.stacksTo(1).component(DataComponents.USE_EFFECTS, use)))
                    : w.plasma() ? ITEMS.registerItem(w.id, p -> new PlasmaGunItem(w, p.stacksTo(1).component(DataComponents.USE_EFFECTS, use)))
                    : ITEMS.registerItem(w.id, p -> new FirearmItem(w, p.stacksTo(1).component(DataComponents.USE_EFFECTS, use)));
            GUNS.put(w, item);
            TAB_ORDER.add(item);
        }
        for (Weapon.Ammo a : Weapon.Ammo.values()) AMMO.put(a, simple(a.id));
        for (Weapon.Magazine m : Weapon.Magazine.values()) {
            DeferredItem<MagazineItem> mag = ITEMS.registerItem(m.id, p -> new MagazineItem(m, p.stacksTo(1)));
            MAGAZINES.put(m, mag);
            TAB_ORDER.add(mag);
        }
        armorSet("musketeer", MUSKETEER);
        armorSet("ballistic", BALLISTIC);
        armorSet("assault", ASSAULT);
        poweredSet("power_armor", POWERED, 40_000_000L, 32768, 5, 10_000, false);
        poweredSet("plasma_armor", PLASMA, 400_000_000L, 131072, 7, 4_000, true);
        for (VehicleKind k : VehicleKind.values()) {
            DeferredHolder<EntityType<?>, EntityType<Driveable>> type = ENTITIES.registerEntityType(k.id,
                    (t, l) -> new Driveable(t, l, k), MobCategory.MISC, b -> b.noLootTable().sized(k.width, k.height).clientTrackingRange(16).updateInterval(1));
            VEHICLE_TYPES.put(k, type);
            DeferredItem<VehicleItem> item = ITEMS.registerItem("vehicle_" + k.id, p -> new VehicleItem(k, type, p.stacksTo(1)));
            VEHICLES.put(k, item);
            TAB_ORDER.add(item);
        }
    }

    /** Детали: колёса и моторы эпох. */
    public static final DeferredItem<Item> WHEEL_WOOD = simple("wheel_wood"), WHEEL_RUBBER = simple("wheel_rubber"), ENGINE_STEAM = simple("engine_steam"), ENGINE_LV = simple("engine_lv"),
            ENGINE_MV = simple("engine_mv"), ENGINE_HV = simple("engine_hv"), ENGINE_EV = simple("engine_ev"), JET_TURBINE = simple("jet_turbine");
    /** Сборочные единицы машин (D130): рецепты в tools/gen/equipment.py. */
    public static final List<DeferredItem<Item>> VEHICLE_PARTS = java.util.stream.Stream.of("chassis_light", "chassis_heavy", "chassis_armored", "vehicle_suspension", "transmission", "fuel_tank", "drivetrain",
            "body_panels", "dashboard", "wing", "propeller", "rotor_blades", "landing_gear", "track_unit", "boat_hull", "tank_turret").map(EquipmentContent::simple).toList();
    /** Грузовой комплект: ПКМ по машине. */
    public static final DeferredItem<Item> CARGO_KIT = simple("cargo_kit");
    /** Детали огнестрела: механика без электроники. */
    public static final List<DeferredItem<Item>> GUN_PARTS = java.util.stream.Stream.of("barrel_steel", "barrel_stainless", "barrel_titanium", "trigger_group", "gun_spring", "scope").map(EquipmentContent::simple).toList();

    public static final net.neoforged.neoforge.registries.DeferredBlock<ru.iis.equipment.vehicle.FuelPumpBlock> FUEL_PUMP = BLOCKS.registerBlock("fuel_pump", ru.iis.equipment.vehicle.FuelPumpBlock::new,
            p -> p.mapColor(net.minecraft.world.level.material.MapColor.METAL).sound(net.minecraft.world.level.block.SoundType.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FUEL_PUMP_ITEM = pumpItem();

    private static DeferredItem<net.minecraft.world.item.BlockItem> pumpItem() {
        DeferredItem<net.minecraft.world.item.BlockItem> i = ITEMS.registerSimpleBlockItem("fuel_pump", FUEL_PUMP);
        TAB_ORDER.add(i);
        return i;
    }

    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<ru.iis.equipment.vehicle.FuelPumpBlockEntity>> FUEL_PUMP_BE =
            BLOCK_ENTITIES.register("fuel_pump", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(ru.iis.equipment.vehicle.FuelPumpBlockEntity::new, FUEL_PUMP.get()));

    public static final DeferredHolder<EntityType<?>, EntityType<ShotEntity>> SHOT = ENTITIES.registerEntityType("shot",
            ShotEntity::new, MobCategory.MISC, b -> b.noLootTable().sized(0.4F, 0.4F).clientTrackingRange(10).updateInterval(1));

    public static final DeferredHolder<MenuType<?>, MenuType<VehicleMenu>> VEHICLE_MENU = MENUS.register("vehicle", () -> IMenuTypeExtension.create(VehicleMenu::client));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("equipment", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.equipment"))
            .icon(() -> new ItemStack(GUNS.get(Weapon.ASSAULT_RIFLE).get()))
            .displayItems((params, out) -> TAB_ORDER.forEach(i -> out.accept(i.get())))
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, FUEL_PUMP_BE.get(), (be, side) -> be.tank));
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        COMPONENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(EquipmentEvents::onPlayerTick);
    }
}
