package ru.iis.multi;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.iis.IIS;

/**
 * Горячие слитки обжигают (GT_Proxy#onPlayerTickEventServer, OrePrefixes.ingotHot.mHeatDamage = 3, D81): раз в 120 тиков
 * каждая стопка горячих слитков в инвентаре и броне наносит 3 урона жаром (GT_DamageSources DamageSourceHeat: зависит от
 * сложности, «сварился заживо»), кроме творческого режима и огнестойкости. Термокостюма GT пока нет; левая рука (её не
 * было в 1.7.10) проверяется вместе с инвентарём.
 */
public final class HotIngotHeat {
    private HotIngotHeat() {}

    public static final int INTERVAL = 120;
    public static final float DAMAGE = 3.0f;
    public static final TagKey<Item> HOT_INGOTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IIS.MODID, "hot_ingots"));
    public static final ResourceKey<DamageType> HEAT = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(IIS.MODID, "heat"));

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % INTERVAL != 0) return;
        if (player.level() instanceof ServerLevel sl) burn(player, sl);
    }

    /** Одна проверка GT: по удару за каждую стопку горячих слитков. */
    public static void burn(net.minecraft.world.entity.player.Player player, ServerLevel sl) {
        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) return;
        int stacks = 0;
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) if (s.is(HOT_INGOTS)) stacks++;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            if (player.getItemBySlot(slot).is(HOT_INGOTS)) stacks++;
        }
        for (int i = 0; i < stacks && !player.hasEffect(MobEffects.FIRE_RESISTANCE); i++) {
            player.hurtServer(sl, sl.damageSources().source(HEAT), DAMAGE);
        }
    }
}
