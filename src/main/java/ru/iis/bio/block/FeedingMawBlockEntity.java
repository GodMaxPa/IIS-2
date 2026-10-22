package ru.iis.bio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.bio.BioContent;
import ru.iis.bio.BioSounds;
import ru.iis.bio.item.BioItems;

/** Пасть-кормушка: проверяет связь с колонией, принимает еду и передаёт питание ядру. */
public class FeedingMawBlockEntity extends BlockEntity {
    private static final int CHEW_TICKS = 40;
    private int chewLeft, checkTimer;

    public FeedingMawBlockEntity(BlockPos pos, BlockState state) {
        super(BioContent.MAW_BE.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        BlockState st = getBlockState();
        if (chewLeft > 0) {
            chewLeft--;
            if (chewLeft == 20) sl.playSound(null, worldPosition, BioSounds.CHEW, SoundSource.BLOCKS, 0.8f, 1.0f);
            if (chewLeft == 0) sl.setBlock(worldPosition, st.setValue(FeedingMawBlock.CHEWING, false), 3);
        }
        if (--checkTimer <= 0) {
            checkTimer = 20;
            boolean connected = ColonyCoreBlockEntity.find(sl, worldPosition) != null;
            BlockState cur = getBlockState();
            if (cur.getValue(FeedingMawBlock.CONNECTED) != connected) {
                sl.setBlock(worldPosition, cur.setValue(FeedingMawBlock.CONNECTED, connected), 3);
            }
        }
    }

    /** Что пасть принимает: любую еду (сырая переваривается хуже, D70 — решение Р3) и подготовленные массы. */
    public static boolean edible(ItemStack stack) {
        if (stack.isEmpty()) return false;
        FoodProperties food = stack.get(DataComponents.FOOD);
        return (food != null && food.nutrition() > 0) || stack.is(BioItems.MEAT_MASS.get()) || stack.is(BioItems.PLANT_MASS.get())
                || stack.is(BioItems.ROTTEN_MASS.get());
    }

    /** Игрок кладёт пищу в пасть: она уходит в пищеварительный карман ядра и переваривается там (не мгновенно). */
    public void feedFrom(Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl) || !edible(stack)) return;
        ColonyCoreBlockEntity core = ColonyCoreBlockEntity.find(sl, worldPosition);
        if (core == null) {
            player.sendOverlayMessage(Component.translatable("message.iis.bio.maw.disconnected"));
            return;
        }
        if (!core.swallow(stack)) {
            player.sendOverlayMessage(Component.translatable("message.iis.bio.maw.full"));
            return;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        chewLeft = CHEW_TICKS;
        BlockState st = getBlockState();
        sl.setBlock(worldPosition, st.setValue(FeedingMawBlock.CHEWING, true).setValue(FeedingMawBlock.CONNECTED, true), 3);
        sl.playSound(null, worldPosition, BioSounds.GULP, SoundSource.BLOCKS, 1.0f, 0.9f + sl.getRandom().nextFloat() * 0.2f);
        sl.sendParticles(ParticleTypes.CRIMSON_SPORE, worldPosition.getX() + 0.5, worldPosition.getY() + 0.7, worldPosition.getZ() + 0.5, 10, 0.25, 0.2, 0.25, 0.02);
        player.sendOverlayMessage(Component.translatable("message.iis.bio.maw.fed", (int) core.colony().nutrition(), core.colony().params().nutritionCap()));
    }
}
