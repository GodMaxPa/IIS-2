package ru.iis.gear;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.item.BatteryItem;

/** Заряжающая батарея IC2: лежит в инвентаре и заряжает панель быстрого доступа и броню. */
public class ChargingBatteryItem extends BatteryItem {
    public ChargingBatteryItem(long capacity, long transfer, int tier, Properties p) {
        super(capacity, transfer, tier, p);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (entity instanceof Player player) GearEvents.chargeFrom(player, stack, tier, transferLimit);
    }
}
