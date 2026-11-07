package ru.iis.space;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Дыхание вне Земли. Как в Galacticraft: маска, оборудование и баллон; 1 единица в секунду; без кислорода — урон.
 * Отличие: у GC свои слоты снаряжения, здесь всё лежит в инвентаре и не занимает слоты брони.
 */
public final class OxygenSystem {
    private OxygenSystem() {}

    public static final int TANK_LIGHT = 900;
    public static final float DAMAGE = 1.0f;

    public static boolean airless(ResourceKey<Level> dim) {
        return dim == SpaceContent.MOON || dim == SpaceContent.MARS || dim == SpaceContent.ASTEROIDS;
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!airless(player.level().dimension()) || player.isCreative() || player.isSpectator()) return;
        if (player.getVehicle() instanceof RocketEntity) return;   // в ракете дышат её воздухом
        if (SpaceMachineBlockEntity.inBubble(player.level(), player.getEyePosition())) return;   // D134: пузырь распределителя
        int left = breathe(player);
        if (left < 0) {
            player.sendOverlayMessage(Component.translatable("message.iis.oxygen.none"));
            if (player.level() instanceof ServerLevel sl) {
                player.hurtServer(sl, new DamageSource(sl.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(SpaceContent.OXYGEN_SUFFOCATION)), DAMAGE);
            }
        } else if (left <= 30) {
            player.sendOverlayMessage(Component.translatable("message.iis.oxygen.low", left));
        }
    }

    /** Один вдох: тратит единицу из первого непустого баллона. Возвращает остаток во всех баллонах или -1, если дышать нечем. */
    public static int breathe(Player player) {
        boolean mask = false, gear = false;
        ItemStack tank = ItemStack.EMPTY;
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(SpaceContent.OXYGEN_MASK.get())) mask = true;
            else if (s.is(SpaceContent.OXYGEN_GEAR.get())) gear = true;
            else if (s.is(SpaceContent.OXYGEN_TANK_LIGHT.get()) && s.getDamageValue() < s.getMaxDamage()) {
                total += s.getMaxDamage() - s.getDamageValue();
                if (tank.isEmpty()) tank = s;
            }
        }
        if (!mask || !gear || tank.isEmpty()) return -1;
        tank.setDamageValue(tank.getDamageValue() + 1);
        return total - 1;
    }
}
