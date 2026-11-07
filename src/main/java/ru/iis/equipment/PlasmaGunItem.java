package ru.iis.equipment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import ru.iis.fluid.FluidContent;

import java.util.function.Consumer;

/** Плазменное оружие (D130): стреляет плазмой из своего бака, без EU. R заливает капсулу плазмы из инвентаря; чем больше энергии в литре, тем сильнее выстрел. */
public class PlasmaGunItem extends Item implements GunActions.Gun {
    private final Weapon weapon;

    public PlasmaGunItem(Weapon weapon, Properties p) {
        super(p);
        this.weapon = weapon;
    }

    @Override
    public Weapon weapon() {
        return weapon;
    }

    public static FluidContent tank(ItemStack stack) {
        return stack.getOrDefault(EquipmentContent.GUN_PLASMA.get(), FluidContent.EMPTY);
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
    public boolean isBarVisible(ItemStack stack) {
        return tank(stack).amount() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * tank(stack).amount() / weapon.plasmaCapacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xC060FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        FirearmItem.tooltip(weapon, out);
        FluidContent c = tank(stack);
        if (c.isEmpty()) {
            out.accept(Component.translatable("tooltip.iis.gun.plasma_empty", weapon.plasmaLiters()).withStyle(ChatFormatting.GRAY));
        } else {
            out.accept(Component.translatable("tooltip.iis.gun.plasma", c.fluidValue().getFluidType().getDescription(), c.amount(), weapon.plasmaCapacity(), weapon.plasmaLiters()).withStyle(ChatFormatting.GRAY));
            out.accept(Component.translatable("tooltip.iis.gun.plasma_power", Math.round(100 * GunActions.plasmaScale(c))).withStyle(ChatFormatting.GRAY));
        }
    }
}
