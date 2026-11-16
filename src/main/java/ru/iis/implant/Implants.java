package ru.iis.implant;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import ru.iis.IIS;
import ru.iis.implant.ImplantLogic.Kind;
import ru.iis.implant.ImplantLogic.Slot;
import ru.iis.lv.item.BatteryItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Киберимпланты (D157): предметы, данные тела игрока и то, что импланты делают.
 * <p>
 * Тело хранится на игроке: список имплантов по областям (остаётся после смерти — ответ владельца) и заряд EU (после смерти
 * обнуляется: не переносится). Раз в секунду считается расход; пока заряда хватает, на игроке висят модификаторы атрибутов.
 * Ставит и снимает импланты хирургический стол ({@link SurgeryTableBlockEntity}).
 */
public final class Implants {
    private Implants() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IIS.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IIS.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IIS.MODID);

    public static final Map<Kind, DeferredItem<Item>> ITEM_OF = new LinkedHashMap<>();

    static {
        for (Kind k : ImplantLogic.KINDS) ITEM_OF.put(k, ITEMS.registerItem(k.id(), p -> new Item(p.stacksTo(1))));
    }

    /** Импланты по областям (пустая строка — области свободна). Переживают смерть. */
    public static final Supplier<AttachmentType<List<String>>> BODY = ATTACHMENTS.register("implants",
            () -> AttachmentType.<List<String>>builder(() -> new ArrayList<>()).serialize(Codec.STRING.listOf().fieldOf("slots"), l -> !l.isEmpty()).copyOnDeath().build());
    /** Заряд тела, EU. Смерть его обнуляет. */
    public static final Supplier<AttachmentType<Long>> CHARGE = ATTACHMENTS.register("implant_charge",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG.fieldOf("eu"), c -> c != 0L).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("implants", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iis.implants"))
            .icon(() -> new ItemStack(ITEM_OF.get(ImplantLogic.KINDS.getFirst()).get()))
            .displayItems((params, out) -> {
                out.accept(ImplantContent.SURGERY_TABLE_ITEM.get());
                ITEM_OF.values().forEach(i -> out.accept(i.get()));
            }).build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        TABS.register(modBus);
        NeoForge.EVENT_BUS.addListener(Implants::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(Implants::registerCommands);
    }

    // --- тело -------------------------------------------------------------------------------------------------------
    public static Kind kindOf(ItemStack stack) {
        for (Map.Entry<Kind, DeferredItem<Item>> e : ITEM_OF.entrySet()) if (stack.is(e.getValue().get())) return e.getKey();
        return null;
    }

    public static Kind[] body(Player player) {
        return ImplantLogic.parse(player.getData(BODY));
    }

    public static void setBody(Player player, Kind[] body) {
        player.setData(BODY, new ArrayList<>(ImplantLogic.ids(body)));
    }

    public static long charge(Player player) {
        return player.getData(CHARGE);
    }

    public static void setCharge(Player player, long eu) {
        player.setData(CHARGE, Math.max(0, eu));
    }

    /** Принять заряд (от стола): возвращает, сколько вошло. */
    public static long addCharge(Player player, long eu) {
        long cap = ImplantLogic.capacity(body(player)), c = charge(player);
        long n = Math.max(0, Math.min(eu, cap - c));
        if (n > 0) setCharge(player, c + n);
        return n;
    }

    // --- работа -----------------------------------------------------------------------------------------------------
    private record Bonus(Slot slot, Holder<Attribute> attribute, String id, double perLevel, AttributeModifier.Operation op, boolean evOnly) {}

    private static final List<Bonus> BONUSES = List.of(
            new Bonus(Slot.BRAIN, Attributes.BLOCK_INTERACTION_RANGE, "brain_reach", 1.0, AttributeModifier.Operation.ADD_VALUE, false),
            new Bonus(Slot.BRAIN, Attributes.ENTITY_INTERACTION_RANGE, "brain_touch", 1.0, AttributeModifier.Operation.ADD_VALUE, false),
            new Bonus(Slot.HEART, Attributes.MAX_HEALTH, "heart_health", 6.0, AttributeModifier.Operation.ADD_VALUE, false),
            new Bonus(Slot.ARMS, Attributes.BLOCK_BREAK_SPEED, "arms_dig", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false),
            new Bonus(Slot.ARMS, Attributes.ATTACK_DAMAGE, "arms_hit", 2.0, AttributeModifier.Operation.ADD_VALUE, false),
            new Bonus(Slot.LEGS, Attributes.MOVEMENT_SPEED, "legs_speed", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false),
            new Bonus(Slot.LEGS, Attributes.JUMP_STRENGTH, "legs_jump", 0.1, AttributeModifier.Operation.ADD_VALUE, false),
            new Bonus(Slot.LEGS, Attributes.SAFE_FALL_DISTANCE, "legs_fall", 3.0, AttributeModifier.Operation.ADD_VALUE, false),
            new Bonus(Slot.LEGS, Attributes.STEP_HEIGHT, "legs_step", 0.2, AttributeModifier.Operation.ADD_VALUE, true),
            new Bonus(Slot.SKIN, Attributes.ARMOR, "skin_armor", 4.0, AttributeModifier.Operation.ADD_VALUE, false),
            new Bonus(Slot.SKIN, Attributes.ARMOR_TOUGHNESS, "skin_tough", 1.0, AttributeModifier.Operation.ADD_VALUE, true));

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        List<String> raw = p.getData(BODY);
        if (raw.isEmpty() && charge(p) == 0) return;
        Kind[] body = ImplantLogic.parse(raw);
        long cap = ImplantLogic.capacity(body), c = Math.min(charge(p), cap);
        if (c < cap) c += fromBatteries(p, Math.min(cap - c, ImplantLogic.BATTERY_CHARGE * 20));
        ImplantLogic.Step step = ImplantLogic.step(body, c, 20);
        if (step.charge() != charge(p)) setCharge(p, step.charge());
        apply(p, body, step.active());
    }

    /** Добрать заряд из батарей в инвентаре. */
    private static long fromBatteries(ServerPlayer p, long want) {
        long got = 0;
        for (int i = 0; i < p.getInventory().getContainerSize() && got < want; i++) {
            ItemStack s = p.getInventory().getItem(i);
            // только батареи: электроинструмент и броня тоже несут заряд, но телу его не отдают
            if (s.getItem() instanceof BatteryItem b && (b.getClass() == BatteryItem.class || b instanceof ru.iis.gear.ChargingBatteryItem)) got += b.discharge(s, want - got);
        }
        return got;
    }

    /** Навесить или снять бонусы. Модификаторы временные: после смерти и перезахода их вернёт ближайшая секунда. */
    public static void apply(Player p, Kind[] body, boolean active) {
        for (Bonus b : BONUSES) {
            AttributeInstance inst = p.getAttribute(b.attribute);
            if (inst == null) continue;
            Kind k = body[b.slot.ordinal()];
            double amount = !active || k == null || (b.evOnly && k.tier() != ImplantLogic.Tier.EV) ? 0 : b.perLevel * k.tier().level;
            Identifier id = Identifier.fromNamespaceAndPath(IIS.MODID, "implant_" + b.id);
            AttributeModifier cur = inst.getModifier(id);
            if (cur != null && cur.amount() == amount) continue;
            if (cur != null) inst.removeModifier(id);
            if (amount != 0) inst.addTransientModifier(new AttributeModifier(id, amount, b.op));
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        if (!active) return;
        Kind eyes = body[Slot.EYES.ordinal()], heart = body[Slot.HEART.ordinal()];
        if (eyes != null) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false, false));
            if (eyes.tier() == ImplantLogic.Tier.EV) {   // улучшенные глаза не слепнут
                p.removeEffect(MobEffects.BLINDNESS);
                p.removeEffect(MobEffects.DARKNESS);
            }
        }
        // улучшенное сердце понемногу лечит: полсердца раз в 5 секунд
        if (heart != null && heart.tier() == ImplantLogic.Tier.EV && p.tickCount % 100 == 0 && p.getHealth() < p.getMaxHealth()) p.heal(1.0F);
    }

    // --- команда снятия: её вызывает строка в сообщении стола -------------------------------------------------------------
    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("iis_implant").then(Commands.literal("remove")
                .then(Commands.argument("slot", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Slot slot = Slot.byId(StringArgumentType.getString(ctx, "slot"));
                    SurgeryTableBlockEntity table = SurgeryTableBlockEntity.near(p);
                    if (slot == null || table == null) {
                        p.sendOverlayMessage(Component.translatable("message.iis.implant.no_table"));
                        return 0;
                    }
                    return table.beginRemove(p, slot) ? 1 : 0;
                }))));
    }
}
