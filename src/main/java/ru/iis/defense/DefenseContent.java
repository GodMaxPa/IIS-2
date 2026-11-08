package ru.iis.defense;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
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
import ru.iis.equipment.EquipmentContent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Оборона (D140): щиты Кибера и турели в духе Open Modular Turrets. */
public final class DefenseContent {
    private DefenseContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IIS.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);
    private static final List<Supplier<? extends Item>> TAB_ORDER = new ArrayList<>();

    /**
     * Баллистический щит MV: 30 % любого урона спереди, удары и снаряды (в том числе пули) — полностью, взрывы — наполовину; прочность 1680
     * (впятеро больше ванильного щита). Числа — предложение ИИ.
     */
    public static final DeferredItem<net.minecraft.world.item.ShieldItem> BALLISTIC_SHIELD = add(ITEMS.registerItem("ballistic_shield", p -> new net.minecraft.world.item.ShieldItem(p.durability(1680)
            .equippableUnswappable(EquipmentSlot.OFFHAND).component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK)
            .delayedComponent(DataComponents.BLOCKS_ATTACKS, ctx -> new BlocksAttacks(0.25F, 1.0F, List.of(
                    new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 0.3F),
                    new BlocksAttacks.DamageReduction(90.0F, Optional.of(ctx.getOrThrow(DamageTypeTags.IS_PROJECTILE)), 0.0F, 0.7F),
                    new BlocksAttacks.DamageReduction(90.0F, Optional.of(direct(ctx, DamageTypes.PLAYER_ATTACK, DamageTypes.MOB_ATTACK, DamageTypes.MOB_ATTACK_NO_AGGRO,
                            DamageTypes.STING, DamageTypes.ARROW, DamageTypes.TRIDENT, DamageTypes.MOB_PROJECTILE, DamageTypes.THROWN, EquipmentContent.DAMAGE_BULLET)), 0.0F, 0.7F),
                    new BlocksAttacks.DamageReduction(90.0F, Optional.of(direct(ctx, DamageTypes.EXPLOSION, DamageTypes.PLAYER_EXPLOSION, EquipmentContent.DAMAGE_BLAST)), 0.0F, 0.2F)),
                    new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F), Optional.of(ctx.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                    Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK))))));
    /** Силовой щит IV: держит всё спереди, топорами не выбивается, тратит заряд ({@link ForceShieldItem}). */
    public static final DeferredItem<ForceShieldItem> FORCE_SHIELD = add(ITEMS.registerItem("force_shield", p -> new ForceShieldItem(p.stacksTo(1)
            .equippableUnswappable(EquipmentSlot.OFFHAND).component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
            .delayedComponent(DataComponents.BLOCKS_ATTACKS, ctx -> new BlocksAttacks(0.1F, 0.0F, List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
                    new BlocksAttacks.ItemDamageFunction(Float.MAX_VALUE, 0.0F, 0.0F), Optional.of(ctx.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                    Optional.of(SoundEvents.SHIELD_BLOCK), Optional.<net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>>empty())))));

    @SafeVarargs
    private static HolderSet<DamageType> direct(net.minecraft.core.HolderGetter.Provider ctx, ResourceKey<DamageType>... keys) {
        return HolderSet.direct(java.util.Arrays.stream(keys).map(k -> (net.minecraft.core.Holder<DamageType>) ctx.getOrThrow(k)).toList());
    }

    public static final List<DeferredBlock<TurretBaseBlock>> BASES = new ArrayList<>();
    public static final Map<TurretKind, DeferredBlock<TurretBlock>> TURRETS = new EnumMap<>(TurretKind.class);

    static {
        for (int t = 1; t <= 5; t++) {
            int tier = t;
            DeferredBlock<TurretBaseBlock> b = BLOCKS.registerBlock("turret_base_t" + t, p -> new TurretBaseBlock(tier, p),
                    p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0f, 10.0f + 10 * tier).requiresCorrectToolForDrops());
            add(ITEMS.registerSimpleBlockItem("turret_base_t" + t, b));
            BASES.add(b);
        }
        for (TurretKind k : TurretKind.values()) {
            DeferredBlock<TurretBlock> b = BLOCKS.registerBlock("turret_" + k.id, p -> new TurretBlock(k, p),
                    p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3.0f, 10.0f).noOcclusion().requiresCorrectToolForDrops());
            add(ITEMS.registerSimpleBlockItem("turret_" + k.id, b));
            TURRETS.put(k, b);
        }
    }

    public static final DeferredItem<Item> UPGRADE_ACCURACY = add(ITEMS.registerSimpleItem("turret_upgrade_accuracy"));
    public static final DeferredItem<Item> UPGRADE_EFFICIENCY = add(ITEMS.registerSimpleItem("turret_upgrade_efficiency"));
    public static final DeferredItem<Item> UPGRADE_FIRE_RATE = add(ITEMS.registerSimpleItem("turret_upgrade_fire_rate"));
    public static final DeferredItem<Item> UPGRADE_RANGE = add(ITEMS.registerSimpleItem("turret_upgrade_range"));
    public static final DeferredItem<Item> ADDON_SOLAR = add(ITEMS.registerSimpleItem("turret_addon_solar"));
    public static final DeferredItem<Item> ADDON_REDSTONE = add(ITEMS.registerSimpleItem("turret_addon_redstone"));
    public static final DeferredItem<Item> ADDON_RECYCLER = add(ITEMS.registerSimpleItem("turret_addon_recycler"));
    public static final DeferredItem<Item> FERRO_SLUG = add(ITEMS.registerSimpleItem("ferro_slug"));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurretBaseBlockEntity>> TURRET_BASE_BE = BLOCK_ENTITIES.register("turret_base",
            () -> new BlockEntityType<>(TurretBaseBlockEntity::new, BASES.stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurretBlockEntity>> TURRET_BE = BLOCK_ENTITIES.register("turret",
            () -> new BlockEntityType<>(TurretBlockEntity::new, TURRETS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));

    private static <I extends Item> DeferredItem<I> add(DeferredItem<I> i) {
        TAB_ORDER.add(i);
        return i;
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("defense", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.defense"))
            .icon(() -> new ItemStack(TURRETS.get(TurretKind.MACHINE_GUN).get()))
            .displayItems((params, out) -> {
                TAB_ORDER.forEach(i -> out.accept(i.get()));
                ru.iis.monitor.MonitorContent.ITEMS.getEntries().forEach(i -> out.accept(i.get()));
            })
            .build());

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, TURRET_BASE_BE.get(), (be, side) -> ru.iis.cover.Covers.items(be, side, be.itemHandler(side)));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(DefenseContent::capabilities);
        NeoForge.EVENT_BUS.addListener(ForceShieldItem::onBlock);
    }
}
