package ru.iis.lv.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.lv.LvContent;
import ru.iis.lv.block.RubberLogBlock;

/** Краник IC2: по мокрому отверстию каучукового дерева — 1–3 липкой смолы (IC2 ItemTreetap#attemptExtract), прочность 16. */
public class TreetapItem extends Item {
    public TreetapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof RubberLogBlock) || s.getValue(RubberLogBlock.RESIN) == RubberLogBlock.Resin.NONE
                || ctx.getClickedFace() != s.getValue(RubberLogBlock.FACING)) return InteractionResult.PASS;
        if (s.getValue(RubberLogBlock.RESIN) == RubberLogBlock.Resin.DRY) {
            if (ctx.getPlayer() != null && !level.isClientSide()) ctx.getPlayer().sendOverlayMessage(Component.translatable("message.iis.resin"));
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel sl) {
            level.setBlock(pos, s.setValue(RubberLogBlock.RESIN, RubberLogBlock.Resin.DRY), 3);
            int n = 1 + sl.getRandom().nextInt(3);
            BlockPos out = pos.relative(ctx.getClickedFace());
            ItemEntity e = new ItemEntity(sl, out.getX() + 0.5, out.getY() + 0.3, out.getZ() + 0.5, new ItemStack(LvContent.STICKY_RESIN.get(), n));
            e.setDefaultPickUpDelay();
            sl.addFreshEntity(e);
            sl.playSound(null, pos, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);
            if (ctx.getPlayer() != null) ctx.getItemInHand().hurtAndBreak(1, ctx.getPlayer(), ctx.getHand());
        }
        return InteractionResult.SUCCESS;
    }
}
