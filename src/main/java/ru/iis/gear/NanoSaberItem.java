package ru.iis.gear;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.item.BatteryItem;

/** Нано-сабля: ПКМ включает. Включённая ест заряд и бьёт на 20 (удар стоит ещё 400 EU), выключенная бьёт как рукой. Числа из памяти об IC2. */
public class NanoSaberItem extends BatteryItem {
    public static final float DAMAGE = 20;
    public static final long EU_PER_TICK = 64, EU_PER_HIT = 400;

    public NanoSaberItem(Properties p) {
        super(160000, 512, 2, p);
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    public static boolean active(ItemStack stack) {
        return stack.getOrDefault(GearContent.MODE.get(), 0) != 0;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            boolean on = !active(stack) && charge(stack) >= EU_PER_TICK;
            stack.set(GearContent.MODE.get(), on ? 1 : 0);
            player.sendOverlayMessage(Component.translatable(on ? "item.iis.nano_saber.on" : "item.iis.nano_saber.off"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!active(stack)) return;
        long c = charge(stack) - EU_PER_TICK;
        setCharge(stack, c);
        if (c <= 0) stack.set(GearContent.MODE.get(), 0);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return active(stack);
    }
}
