package ru.iis.bio.production;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ru.iis.bio.item.LadleItem;

import java.util.List;

/**
 * Ручной перенос жидкостей (Р6): ведро воды наливается во входной бак, мерный ковш наливает порцию во входной бак или набирает
 * порцию из бака продукта (а если продукта нет — из входного).
 */
public final class FluidHands {
    private FluidHands() {}

    /** true — предмет в руке был ведром или ковшом и действие обработано (даже если налить было некуда). */
    public static boolean handle(Level level, Player player, InteractionHand hand, ItemStack stack, List<BioTank> tanks) {
        if (stack.is(Items.WATER_BUCKET)) {
            if (level.isClientSide()) return true;
            for (BioTank t : tanks) {
                if (!t.isOutput() && t.fill(Fluids.WATER, 1000, true) == 1000) {
                    t.fill(Fluids.WATER, 1000, false);
                    if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                    level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 1.0f);
                    return true;
                }
            }
            player.sendOverlayMessage(Component.translatable("message.iis.bio.tank.no_room"));
            return true;
        }
        if (!(stack.getItem() instanceof LadleItem)) return false;
        if (level.isClientSide()) return true;
        LadleItem.Content c = LadleItem.content(stack);
        if (!c.empty()) {
            Fluid f = c.fluidValue();
            int dose = Math.min(c.dose(), c.amount());
            for (BioTank t : tanks) {
                if (t.isOutput()) continue;
                int n = t.fill(f, dose, false);
                if (n > 0) {
                    LadleItem.set(stack, c.with(f, c.amount() - n));
                    level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8f, 1.0f);
                    player.sendOverlayMessage(Component.translatable("message.iis.bio.ladle.poured_into", n, f.getFluidType().getDescription()));
                    return true;
                }
            }
            // некуда налить — пробуем добрать той же жидкости из бака продукта
            if (c.amount() < LadleItem.CAPACITY && take(level, player, stack, tanks, c, true, f)) return true;
            player.sendOverlayMessage(Component.translatable("message.iis.bio.tank.no_room"));
            return true;
        }
        if (take(level, player, stack, tanks, c, true, Fluids.EMPTY)) return true;
        if (take(level, player, stack, tanks, c, false, Fluids.EMPTY)) return true;
        player.sendOverlayMessage(Component.translatable("message.iis.bio.tank.empty"));
        return true;
    }

    private static boolean take(Level level, Player player, ItemStack stack, List<BioTank> tanks, LadleItem.Content c, boolean fromOutput, Fluid only) {
        for (BioTank t : tanks) {
            if (t.isOutput() != fromOutput || t.amount() <= 0) continue;
            Fluid f = t.fluid();
            if (only != Fluids.EMPTY && f != only) continue;
            int want = Math.min(c.dose(), LadleItem.CAPACITY - c.amount());
            int n = t.drain(f, want, false);
            if (n > 0) {
                LadleItem.set(stack, c.with(f, c.amount() + n));
                level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8f, 1.0f);
                player.sendOverlayMessage(Component.translatable("message.iis.bio.ladle.took", n, f.getFluidType().getDescription()));
                return true;
            }
        }
        return false;
    }
}
