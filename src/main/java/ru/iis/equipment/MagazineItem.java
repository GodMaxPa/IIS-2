package ru.iis.equipment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import java.util.function.Consumer;

/** Многоразовый магазин (D130): держите ПКМ — набивается патронами из инвентаря, по одному. R на оружии меняет его на самый полный. */
public class MagazineItem extends Item {
    public static final int TICKS_PER_ROUND = 3;
    public final Weapon.Magazine type;

    public MagazineItem(Weapon.Magazine type, Properties p) {
        super(p);
        this.type = type;
    }

    public static int rounds(ItemStack stack) {
        return stack.getOrDefault(EquipmentContent.GUN_AMMO.get(), 0);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (rounds(player.getItemInHand(hand)) >= type.capacity) return InteractionResult.PASS;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof Player p) || remaining % TICKS_PER_ROUND != 0) return;
        int n = rounds(stack);
        if (n >= type.capacity) {
            p.stopUsingItem();
            return;
        }
        if (GunActions.free(p) || GunActions.takeAmmo(p, type.ammo, 1) == 1) {
            stack.set(EquipmentContent.GUN_AMMO.get(), n + 1);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.35F, 1.7F);
        } else {
            p.stopUsingItem();
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BLOCK;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return rounds(stack) > 0 && rounds(stack) < type.capacity;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * rounds(stack) / type.capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE0B040;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.iis.magazine.rounds", rounds(stack), type.capacity, Component.translatable("item.iis." + type.ammo.id)).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.iis.magazine.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
