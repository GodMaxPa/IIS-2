package ru.iis.equipment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** Порох и патроны: ПКМ стреляет, удержание — очередь у автомата, R перезаряжает, присесть — прицел. */
public class FirearmItem extends Item implements GunActions.Gun {
    private final Weapon weapon;

    public FirearmItem(Weapon weapon, Properties p) {
        super(p);
        this.weapon = weapon;
    }

    @Override
    public Weapon weapon() {
        return weapon;
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
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        GunActions.tickReload(level, owner, stack, weapon);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int n = GunActions.loaded(stack);
        return n > 0 && n < weapon.magazine;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * GunActions.loaded(stack) / weapon.magazine);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE0B040;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        tooltip(weapon, out);
        String feed = weapon.magazineType() != null ? "item.iis." + weapon.magazineType().id : "item.iis." + weapon.ammo.id;
        out.accept(Component.translatable(weapon.magazineType() != null ? "tooltip.iis.gun.magazine_item" : "tooltip.iis.gun.magazine", GunActions.loaded(stack), weapon.magazine, Component.translatable(feed)).withStyle(ChatFormatting.GRAY));
    }

    static void tooltip(Weapon w, Consumer<Component> out) {
        String dmg = w.pellets > 1 ? w.pellets + " × " + fmt(w.damage) : fmt(w.damage);
        out.accept(Component.translatable("tooltip.iis.gun.damage", dmg, w.range, fmt(20.0 / w.interval)).withStyle(ChatFormatting.GRAY));
        if (w.blast > 0) out.accept(Component.translatable("tooltip.iis.gun.blast", fmt(w.blast)).withStyle(ChatFormatting.GRAY));
        if (w.pierce > 0) out.accept(Component.translatable("tooltip.iis.gun.pierce", w.pierce).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable(w.automatic ? "tooltip.iis.gun.auto" : "tooltip.iis.gun.semi").withStyle(ChatFormatting.DARK_GRAY));
    }

    static String fmt(double v) {
        return v == Math.rint(v) ? Integer.toString((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
