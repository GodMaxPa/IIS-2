package ru.iis.gear;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.iis.lv.item.BatteryItem;
import ru.iis.reactor.ReactorContent;

import java.util.function.Predicate;

/** Что делает надетое снаряжение: поглощение урона, падение, ранец, поножи, шлем, зарядка из ранца. */
public final class GearEvents {
    private GearEvents() {}

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Задаёт клиент (GearClient): сервер узнаёт о прыжке из Input игрока. */
    public static Predicate<Player> clientJump = p -> false;

    // --- урон -------------------------------------------------------------------------------------------------------------
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (e.getSource().getEntity() instanceof LivingEntity attacker && e.getSource().getDirectEntity() == attacker) {
            ItemStack hand = attacker.getMainHandItem();
            if (hand.getItem() instanceof NanoSaberItem && NanoSaberItem.active(hand) && BatteryItem.charge(hand) >= NanoSaberItem.EU_PER_HIT) {
                BatteryItem.setCharge(hand, BatteryItem.charge(hand) - NanoSaberItem.EU_PER_HIT);
                e.setAmount(Math.max(e.getAmount(), NanoSaberItem.DAMAGE));
            }
        }
        if (e.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
        e.setAmount(absorb(e.getEntity(), e.getAmount()));
    }

    /** Поглотить урон броней на сущности; вернёт остаток. */
    public static float absorb(LivingEntity entity, float amount) {
        double[] ratio = new double[4];
        long[] charge = new long[4], price = new long[4];
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            ItemStack s = entity.getItemBySlot(ARMOR[i]);
            if (s.getItem() instanceof GearArmorItem a && a.ratio > 0) {
                ratio[i] = GearLogic.baseAbsorption(i) * a.ratio;
                charge[i] = BatteryItem.charge(s);
                price[i] = a.euPerDamage;
                any = true;
            }
        }
        if (!any) return amount;
        GearLogic.Absorb r = GearLogic.absorb(amount, ratio, charge, price);
        for (int i = 0; i < 4; i++) {
            if (r.used()[i] > 0) {
                ItemStack s = entity.getItemBySlot(ARMOR[i]);
                BatteryItem.setCharge(s, BatteryItem.charge(s) - r.used()[i]);
            }
        }
        return r.left();
    }

    public static void onFall(LivingFallEvent e) {
        ItemStack feet = e.getEntity().getItemBySlot(EquipmentSlot.FEET);
        if (!(feet.getItem() instanceof GearArmorItem a) || a.ratio <= 0) return;
        long c = BatteryItem.charge(feet);
        long cost = GearLogic.fallCost(e.getDistance(), a.euPerDamage, c);
        if (cost < 0 || c <= 0) return;
        if (!e.getEntity().level().isClientSide()) BatteryItem.setCharge(feet, c - cost);
        e.setCanceled(true);
    }

    // --- тик игрока -------------------------------------------------------------------------------------------------------
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (p.isSpectator()) return;
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD), chest = p.getItemBySlot(EquipmentSlot.CHEST), legs = p.getItemBySlot(EquipmentSlot.LEGS);
        jetpack(p, chest);
        quantumLegs(p, legs);
        if (p.level().isClientSide()) return;
        if (head.getItem() instanceof GearArmorItem) helmet(p, head);
        if (chest.is(GearContent.QUANTUM_CHESTPLATE.get()) && BatteryItem.charge(chest) > 0 && p.isOnFire()) p.clearFire();
        if (chest.getItem() instanceof GearArmorItem a && a.isBattery()) chargeFrom(p, chest, a.tier, a.transferLimit);
    }

    /** Ранец: пока зажат прыжок. Движение считает клиент, заряд — сервер. */
    private static void jetpack(Player p, ItemStack chest) {
        if (!chest.is(GearContent.JETPACK.get()) || p.getAbilities().flying) return;
        boolean client = p.level().isClientSide();
        boolean jump = client ? clientJump.test(p) : p instanceof ServerPlayer sp && sp.getLastClientInput().jump();
        if (!jump) return;
        long c = BatteryItem.charge(chest);
        double power = GearLogic.jetPower(c, ((BatteryItem) chest.getItem()).capacity, p.getY(), p.level().getMinY(), p.level().getHeight());
        if (power <= 0) return;
        if (client) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, GearLogic.jetMotion(v.y, power), v.z);
            p.moveRelative((float) (0.02 * power), new Vec3(p.xxa, 0, p.zza));
        } else {
            BatteryItem.setCharge(chest, c - GearLogic.JETPACK_EU_PER_TICK);
        }
        p.resetFallDistance();
    }

    /** Квантовые поножи: бег быстрее, 1000 EU за 10 тиков. */
    private static void quantumLegs(Player p, ItemStack legs) {
        if (!legs.is(GearContent.QUANTUM_LEGGINGS.get()) || !p.isSprinting() || !(p.onGround() || p.isInWater()) || BatteryItem.charge(legs) < 1000) return;
        if (p.level().isClientSide()) p.moveRelative(0.1F, new Vec3(0, 0, 1));
        else if (p.tickCount % 10 == 0) BatteryItem.setCharge(legs, BatteryItem.charge(legs) - 1000);
    }

    private static void helmet(Player p, ItemStack head) {
        long c = BatteryItem.charge(head);
        boolean sees = head.is(GearContent.NIGHTVISION_GOGGLES.get()) || head.is(GearContent.NANO_HELMET.get()) || head.is(GearContent.QUANTUM_HELMET.get()) || (head.getItem() instanceof GearArmorItem g && g.nightVision());
        if (sees && GearArmorItem.on(head) && c > 0) {
            BatteryItem.setCharge(head, c - 1);
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false));
        }
        if (!head.is(GearContent.QUANTUM_HELMET.get())) return;
        // Квантовый шлем: воздух и снятие отрав (без капсул воздуха и консервов: их здесь нет)
        c = BatteryItem.charge(head);
        if (p.getAirSupply() <= 100 && c >= 1000) {
            p.setAirSupply(p.getMaxAirSupply());
            BatteryItem.setCharge(head, c - 1000);
        }
        for (var effect : java.util.List.of(MobEffects.POISON, MobEffects.WITHER, ReactorContent.RADIATION)) {
            c = BatteryItem.charge(head);
            if (c >= 5000 && p.hasEffect(effect)) {
                p.removeEffect(effect);
                BatteryItem.setCharge(head, c - 5000);
            }
        }
    }

    /** Зарядить панель быстрого доступа и броню из source. Заряжающие батареи друг друга не трогают. */
    public static void chargeFrom(Player p, ItemStack source, int tier, long limit) {
        long c = BatteryItem.charge(source);
        if (c <= 0) return;
        for (int i = 0; i < 9 + 4 && c > 0; i++) {
            ItemStack s = i < 9 ? p.getInventory().getItem(i) : p.getItemBySlot(ARMOR[i - 9]);
            if (s == source || !(s.getItem() instanceof BatteryItem b) || b.tier > tier || s.getItem() instanceof ChargingBatteryItem) continue;
            if (s.getItem() instanceof GearArmorItem a && a.isBattery()) continue;
            long d = b.charge(s, Math.min(c, limit));
            c -= d;
        }
        BatteryItem.setCharge(source, c);
    }

    /** Квантовые ботинки: прыжок с разбега на 9 блоков, 4000 EU. */
    public static void onJump(LivingEvent.LivingJumpEvent e) {
        LivingEntity l = e.getEntity();
        ItemStack feet = l.getItemBySlot(EquipmentSlot.FEET);
        if (!feet.is(GearContent.QUANTUM_BOOTS.get()) || !l.isSprinting() || BatteryItem.charge(feet) < 4000) return;
        Vec3 v = l.getDeltaMovement();
        l.setDeltaMovement(v.x, 0.9, v.z);
        if (!l.level().isClientSide()) BatteryItem.setCharge(feet, BatteryItem.charge(feet) - 4000);
    }

    /** Клавиша ночного зрения (сервер). */
    public static void toggleNightVision(Entity entity) {
        if (!(entity instanceof Player p)) return;
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.is(GearContent.NIGHTVISION_GOGGLES.get()) || head.is(GearContent.NANO_HELMET.get()) || head.is(GearContent.QUANTUM_HELMET.get()) || (head.getItem() instanceof GearArmorItem g && g.nightVision()))) return;
        boolean on = !GearArmorItem.on(head);
        head.set(GearContent.MODE.get(), on ? 1 : 0);
        if (!on) p.removeEffect(MobEffects.NIGHT_VISION);
        p.sendOverlayMessage(Component.translatable(on ? "gear.iis.night_vision.on" : "gear.iis.night_vision.off"));
    }
}
