package ru.iis.equipment;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.iis.IIS;
import ru.iis.lv.item.BatteryItem;
import ru.iis.reactor.ReactorContent;

/** Что умеет надетая силовая и плазменная броня (D128). Всё только при заряде. */
public final class EquipmentEvents {
    private EquipmentEvents() {}

    private static final Identifier STEP_ID = Identifier.fromNamespaceAndPath(IIS.MODID, "armor_step");
    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(IIS.MODID, "armor_speed");

    private static boolean charged(ItemStack s, long need) {
        return s.getItem() instanceof PoweredArmorItem && BatteryItem.charge(s) >= need;
    }

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (p.level().isClientSide() || p.isSpectator()) return;
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD), chest = p.getItemBySlot(EquipmentSlot.CHEST), legs = p.getItemBySlot(EquipmentSlot.LEGS), feet = p.getItemBySlot(EquipmentSlot.FEET);
        // кираса: не горит
        if (charged(chest, 100) && p.isOnFire()) {
            p.clearFire();
            BatteryItem.setCharge(chest, BatteryItem.charge(chest) - 100);
        }
        // поножи: быстрее бег; ботинки: шаг на блок
        modifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID, charged(legs, 1), legs.getItem() instanceof PoweredArmorItem a && a.plasma ? 0.5 : 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(p, Attributes.STEP_HEIGHT, STEP_ID, charged(feet, 1), 0.5, AttributeModifier.Operation.ADD_VALUE);
        if (charged(legs, 8) && p.isSprinting() && p.tickCount % 10 == 0) BatteryItem.setCharge(legs, BatteryItem.charge(legs) - 800);
        // плазменный шлем: воздух и снятие отрав, как у квантового
        if (head.getItem() instanceof PoweredArmorItem h && h.plasma) {
            if (p.getAirSupply() <= 100 && BatteryItem.charge(head) >= 500) {
                p.setAirSupply(p.getMaxAirSupply());
                BatteryItem.setCharge(head, BatteryItem.charge(head) - 500);
            }
            for (var effect : java.util.List.of(MobEffects.POISON, MobEffects.WITHER, ReactorContent.RADIATION)) {
                if (BatteryItem.charge(head) >= 2000 && p.hasEffect(effect)) {
                    p.removeEffect(effect);
                    BatteryItem.setCharge(head, BatteryItem.charge(head) - 2000);
                }
            }
        }
    }

    private static void modifier(Player p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, Identifier id, boolean on, double value, AttributeModifier.Operation op) {
        var inst = p.getAttribute(attr);
        if (inst == null) return;
        boolean has = inst.getModifier(id) != null;
        if (on && (!has || inst.getModifier(id).amount() != value)) {
            inst.removeModifier(id);
            inst.addTransientModifier(new AttributeModifier(id, value, op));
        } else if (!on && has) {
            inst.removeModifier(id);
        }
    }
}
