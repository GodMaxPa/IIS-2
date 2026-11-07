package ru.iis.lv.item;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;

/** Спичка GT (Behaviour_Lighter с одним зарядом, D132): поджигает как огниво и расходуется. */
public class MatchItem extends Item {
    public MatchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Level level = c.getLevel();
        BlockPos pos = c.getClickedPos().relative(c.getClickedFace());
        if (!BaseFireBlock.canBePlacedAt(level, pos, c.getHorizontalDirection())) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1F, 1.2F);
        level.setBlock(pos, BaseFireBlock.getState(level, pos), 11);
        if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
