package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.block.LvMachineBlock;
import ru.iis.machine.block.Wrench;
import ru.iis.multi.block.MaintenanceHatchBlockEntity;
import ru.iis.multi.logic.Maintenance;

/** Блок автоматики GT (D108): отвёртка настраивает, ключ ставит заднюю сторону (выход) на кликнутую грань, как в GT. */
public class AutomationBlock extends LvMachineBlock {
    public final AutomationType kind;
    public final VoltageTier tier;

    public AutomationBlock(AutomationType kind, VoltageTier tier, Properties properties) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutomationBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AutomationBlockEntity be) {
            Direction side = hit.getDirection();
            if (MaintenanceHatchBlockEntity.problemFor(stack) == Maintenance.Problem.SCREWDRIVER && be.covers().get(side) == null) {
                if (!level.isClientSide()) be.screwdriver(side, player);
                return InteractionResult.SUCCESS;
            }
            if (Wrench.is(stack) && !player.isShiftKeyDown() && side.getAxis().isHorizontal() && be.covers().get(side) == null) {
                if (!level.isClientSide()) Wrench.rotate(level, pos, state, FACING, side.getOpposite(), player, hand, stack);
                return InteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** GT setInternalOutputRedstoneSignal: сигнал заполнения со всех сторон, поверх сигналов покрытий. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        int own = level.getBlockEntity(pos) instanceof AutomationBlockEntity be ? be.signal() : 0;
        return Math.max(own, super.getSignal(state, level, pos, direction));
    }
}
