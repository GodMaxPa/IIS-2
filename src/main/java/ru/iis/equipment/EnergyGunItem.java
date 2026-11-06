package ru.iis.equipment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import ru.iis.lv.item.BatteryItem;

import java.util.function.Consumer;

/** Лазер и плазма: заряд вместо патронов, заряжается в зарядниках машин и от ранцев, как электроинструмент. */
public class EnergyGunItem extends BatteryItem implements GunActions.Gun {
    private final Weapon weapon;

    public EnergyGunItem(Weapon weapon, Properties p) {
        super(weapon.capacity, Math.max(2048, weapon.euPerShot), weapon.tier, p);
        this.weapon = weapon;
    }

    @Override
    public Weapon weapon() {
        return weapon;
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        if (level instanceof ServerLevel sl) GunActions.tryFire(sl, player, stack, weapon, true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (weapon.automatic && level instanceof ServerLevel sl) GunActions.tryFire(sl, entity, stack, weapon, false);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        FirearmItem.tooltip(weapon, out);
        out.accept(Component.translatable("tooltip.iis.gun.energy", weapon.euPerShot, charge(stack) / weapon.euPerShot).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, out, flag);
    }
}
