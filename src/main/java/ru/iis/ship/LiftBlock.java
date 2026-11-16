package ru.iis.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Грузовой лифт корабля (CONCEPT §6.1): площадка в полу. ПКМ — вниз, Shift + ПКМ — вверх: игрок переносится на ближайшую
 * площадку в том же столбце, не дальше {@link #RANGE} блоков. Свой лифт строится так же: две площадки одна над другой.
 */
public class LiftBlock extends Block {
    public static final int RANGE = 16;

    public LiftBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int dir = player.isShiftKeyDown() ? 1 : -1;
        BlockPos target = findPad(level, pos, dir);
        if (target == null) target = findPad(level, pos, -dir);   // с крайней площадки лифт идёт в единственную сторону
        if (target == null) {
            player.sendOverlayMessage(Component.translatable("message.iis.ship.lift.none"));
            return InteractionResult.SUCCESS;
        }
        player.teleportTo(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
        player.resetFallDistance();
        level.playSound(null, target, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.8f, 0.7f);
        return InteractionResult.SUCCESS;
    }

    /** Ближайшая площадка в столбце, над которой можно встать. */
    public static BlockPos findPad(Level level, BlockPos from, int dir) {
        for (int i = 2; i <= RANGE; i++) {
            BlockPos p = from.above(dir * i);
            if (level.isOutsideBuildHeight(p)) break;
            if (level.getBlockState(p).getBlock() instanceof LiftBlock && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && level.getBlockState(p.above(2)).getCollisionShape(level, p.above(2)).isEmpty()) return p;
        }
        return null;
    }
}
