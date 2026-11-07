package ru.iis.gear;

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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.item.BatteryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Снаряжение IC2 (D118): батареи и кристаллы, броня, ранцы, лазер, сабля, энергохранилища. Свой код и свои текстуры (D5). */
public final class GearContent {
    private GearContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IIS.MODID);

    /** Режим предмета: лазер — номер, сабля и очки — включён или нет. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MODE = COMPONENTS.registerComponentType("gear_mode",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();

    private static DeferredItem<Item> item(String id) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(id);
        TAB_ORDER.add(i);
        return i;
    }

    private static DeferredItem<BatteryItem> battery(String id, long capacity, long transfer, int tier) {
        DeferredItem<BatteryItem> i = ITEMS.registerItem(id, p -> new BatteryItem(capacity, transfer, tier, p), p -> p.stacksTo(1));
        TAB_ORDER.add(i);
        return i;
    }

    private static DeferredItem<ChargingBatteryItem> charging(String id, long capacity, long transfer, int tier) {
        DeferredItem<ChargingBatteryItem> i = ITEMS.registerItem(id, p -> new ChargingBatteryItem(capacity, transfer, tier, p), p -> p.stacksTo(1));
        TAB_ORDER.add(i);
        return i;
    }

    private static ArmorMaterial material(String asset) {
        ResourceKey<EquipmentAsset> key = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(IIS.MODID, asset));
        // защита без заряда не работает, поэтому очков брони нет: всё считает GearEvents
        return new ArmorMaterial(1, Map.of(ArmorType.BOOTS, 0, ArmorType.LEGGINGS, 0, ArmorType.CHESTPLATE, 0, ArmorType.HELMET, 0, ArmorType.BODY, 0),
                1, SoundEvents.ARMOR_EQUIP_IRON, 0F, 0F, ItemTags.REPAIRS_IRON_ARMOR, key);
    }

    private static final ArmorMaterial NANO = material("nano"), QUANTUM = material("quantum"), GOGGLES = material("goggles"), JET = material("jetpack"), PACK = material("batpack"),
            ADV_PACK = material("adv_batpack"), ENERGY_PACK_M = material("energy_pack"), LAPPACK_M = material("lappack");

    private static DeferredItem<GearArmorItem> armor(String id, ArmorMaterial m, ArmorType type, long capacity, long transfer, int tier, double ratio, long euPerDamage, boolean battery) {
        DeferredItem<GearArmorItem> i = ITEMS.registerItem(id, p -> new GearArmorItem(capacity, transfer, tier, ratio, euPerDamage, battery,
                p.humanoidArmor(m, type).component(DataComponents.UNBREAKABLE, Unit.INSTANCE)));
        TAB_ORDER.add(i);
        return i;
    }

    private static DeferredItem<GearArmorItem> nano(String id, ArmorType t) {
        return armor(id, NANO, t, 1_000_000, 1600, 3, GearLogic.NANO_RATIO, GearLogic.NANO_EU_PER_DAMAGE, false);
    }

    private static DeferredItem<GearArmorItem> quantum(String id, ArmorType t) {
        return armor(id, QUANTUM, t, 10_000_000, 12000, 4, GearLogic.QUANTUM_RATIO, GearLogic.QUANTUM_EU_PER_DAMAGE, false);
    }

    // --- батареи: ёмкости из scripts/GT_IC2.zs:838-863; тир и передача из памяти об IC2 --------------------------------------
    public static final DeferredItem<BatteryItem> BATTERY_RE = battery("battery_re", 10_000, 100, 1);
    public static final DeferredItem<BatteryItem> BATTERY_ADV = battery("battery_adv", 100_000, 256, 2);
    public static final DeferredItem<BatteryItem> ENERGY_CRYSTAL = battery("energy_crystal", 1_000_000, 2048, 3);
    public static final DeferredItem<BatteryItem> LAPOTRON_CRYSTAL = battery("lapotron_crystal", 10_000_000, 8192, 4);
    /** GT_MetaGenerated_Item_01:473, 479. */
    public static final DeferredItem<BatteryItem> ENERGY_ORB = battery("energy_orb", 100_000_000L, 8192, 5);
    public static final DeferredItem<BatteryItem> ENERGY_ORB_CLUSTER = battery("energy_orb_cluster", 1_000_000_000L, 32768, 6);
    /** D132: GT_MetaGenerated_Item_01:843-847, ветка EnableZPMandUVBatteries: 10 и 100 млрд EU, ZPM и UV. */
    public static final DeferredItem<BatteryItem> ENERGY_MODULE = battery("energy_module", 10_000_000_000L, 131072, 7);
    public static final DeferredItem<BatteryItem> ENERGY_CLUSTER = battery("energy_cluster", 100_000_000_000L, 524288, 8);
    /** D121: GT ZPM2, Item_01:849-850 Long.MAX_VALUE, V[8], уровень 8; поведение Behaviour_UB (достижение) не перенесено. */
    public static final DeferredItem<BatteryItem> ULTIMATE_BATTERY = battery("ultimate_battery", Long.MAX_VALUE, 524288, 8);
    public static final DeferredItem<ChargingBatteryItem> CHARGING_RE = charging("charging_battery_re", 40_000, 128, 1);
    public static final DeferredItem<ChargingBatteryItem> CHARGING_ADV = charging("charging_battery_adv", 400_000, 1024, 2);
    public static final DeferredItem<ChargingBatteryItem> CHARGING_CRYSTAL = charging("charging_energy_crystal", 4_000_000, 8192, 3);
    public static final DeferredItem<ChargingBatteryItem> CHARGING_LAPOTRON = charging("charging_lapotron_crystal", 40_000_000, 32768, 4);

    // --- части ----------------------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> ENERGIUM_DUST = item("energium_dust");
    public static final DeferredItem<Item> CRYSTAL_CHIP_MASTER = item("crystal_chip_master");
    public static final DeferredItem<Item> CASING_COPPER = item("item_casing_copper");
    public static final DeferredItem<Item> CASING_TIN = item("item_casing_tin");
    public static final DeferredItem<Item> CASING_BRONZE = item("item_casing_bronze");
    public static final DeferredItem<Item> CASING_GOLD = item("item_casing_gold");
    public static final DeferredItem<Item> CASING_IRON = item("item_casing_iron");
    public static final DeferredItem<Item> CASING_LEAD = item("item_casing_lead");
    public static final DeferredItem<Item> COAL_BALL = item("coal_ball");
    // D119: остатки GT
    public static final DeferredItem<Item> SCRAP = item("scrap");
    public static final DeferredItem<Item> DUCT_TAPE = item("duct_tape");
    public static final DeferredItem<Item> AUTO_REPAIR_KIT = item("auto_repair_kit");
    public static final DeferredItem<Item> STEM_CELL = item("stem_cell");
    public static final List<DeferredItem<Item>> CREDITS = java.util.stream.Stream.of("copper", "cupronickel", "silver", "gold", "platinum", "osmium", "naquadah", "neutronium").map(s -> item("credit_" + s)).toList();
    public static final DeferredItem<Item> COMPRESSED_COAL_BALL = item("compressed_coal_ball");
    public static final DeferredItem<Item> COAL_CHUNK = item("coal_chunk");
    public static final DeferredItem<Item> INDUSTRIAL_DIAMOND = item("industrial_diamond");

    // --- броня, ранцы -------------------------------------------------------------------------------------------------------
    public static final DeferredItem<GearArmorItem> NANO_HELMET = nano("nano_helmet", ArmorType.HELMET);
    public static final DeferredItem<GearArmorItem> NANO_CHESTPLATE = nano("nano_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<GearArmorItem> NANO_LEGGINGS = nano("nano_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<GearArmorItem> NANO_BOOTS = nano("nano_boots", ArmorType.BOOTS);
    public static final DeferredItem<GearArmorItem> QUANTUM_HELMET = quantum("quantum_helmet", ArmorType.HELMET);
    public static final DeferredItem<GearArmorItem> QUANTUM_CHESTPLATE = quantum("quantum_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<GearArmorItem> QUANTUM_LEGGINGS = quantum("quantum_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<GearArmorItem> QUANTUM_BOOTS = quantum("quantum_boots", ArmorType.BOOTS);
    public static final DeferredItem<GearArmorItem> NIGHTVISION_GOGGLES = armor("nightvision_goggles", GOGGLES, ArmorType.HELMET, 200_000, 256, 1, 0, 0, false);
    public static final DeferredItem<GearArmorItem> JETPACK = armor("jetpack", JET, ArmorType.CHESTPLATE, 30_000, 64, 1, 0, 0, false);
    public static final DeferredItem<GearArmorItem> BATPACK = armor("batpack", PACK, ArmorType.CHESTPLATE, 60_000, 100, 1, 0, 0, true);
    public static final DeferredItem<GearArmorItem> ADV_BATPACK = armor("adv_batpack", ADV_PACK, ArmorType.CHESTPLATE, 600_000, 1000, 2, 0, 0, true);
    public static final DeferredItem<GearArmorItem> ENERGY_PACK = armor("energy_pack", ENERGY_PACK_M, ArmorType.CHESTPLATE, 2_000_000, 2048, 3, 0, 0, true);
    public static final DeferredItem<GearArmorItem> LAPPACK = armor("lappack", LAPPACK_M, ArmorType.CHESTPLATE, 10_000_000, 8192, 4, 0, 0, true);

    public static final DeferredItem<MiningLaserItem> MINING_LASER = register("mining_laser", MiningLaserItem::new);
    public static final DeferredItem<NanoSaberItem> NANO_SABER = register("nano_saber", NanoSaberItem::new);

    private static <I extends Item> DeferredItem<I> register(String id, java.util.function.Function<Item.Properties, I> f) {
        DeferredItem<I> i = ITEMS.registerItem(id, f, p -> p.stacksTo(1));
        TAB_ORDER.add(i);
        return i;
    }

    // --- блоки -----------------------------------------------------------------------------------------------------------------
    private static Block.Properties metal(Block.Properties p) {
        return p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops();
    }

    private static DeferredBlock<EnergyStorageBlock> storage(String id, long capacity, VoltageTier tier) {
        DeferredBlock<EnergyStorageBlock> b = BLOCKS.registerBlock(id, p -> new EnergyStorageBlock(capacity, tier, p), GearContent::metal);
        TAB_ORDER.add(ITEMS.registerSimpleBlockItem(id, b));
        return b;
    }

    public static final DeferredBlock<EnergyStorageBlock> BATBOX = storage("batbox", 40_000, VoltageTier.LV);
    public static final DeferredBlock<EnergyStorageBlock> CESU = storage("cesu", 300_000, VoltageTier.MV);
    public static final DeferredBlock<EnergyStorageBlock> MFE = storage("mfe", 4_000_000, VoltageTier.HV);
    public static final DeferredBlock<EnergyStorageBlock> MFSU = storage("mfsu", 40_000_000, VoltageTier.EV);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyStorageBlockEntity>> STORAGE_BE = BLOCK_ENTITIES.register("energy_storage",
            () -> new BlockEntityType<>(EnergyStorageBlockEntity::new, BATBOX.get(), CESU.get(), MFE.get(), MFSU.get()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("gear", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.gear"))
            .icon(() -> new ItemStack(QUANTUM_CHESTPLATE.get()))
            .displayItems((params, out) -> TAB_ORDER.forEach(i -> out.accept(i.get())))
            .build());

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, STORAGE_BE.get(), (be, side) -> ru.iis.cover.Covers.items(be, side, be.itemHandler(side)));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(GearContent::capabilities);
        NeoForge.EVENT_BUS.addListener(GearEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(GearEvents::onFall);
        NeoForge.EVENT_BUS.addListener(GearEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(GearEvents::onJump);
    }
}
