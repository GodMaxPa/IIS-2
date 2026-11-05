package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.block.LvMachineBlock;
import ru.iis.multi.block.MaintenanceHatchBlockEntity;
import ru.iis.multi.logic.Maintenance;

/** Отпугиватель монстров GT5-II (GT_MetaTileEntity_MonsterRepellent, D109): отвёртка меняет режим. */
public class MonsterRepellentBlock extends LvMachineBlock {
    public final VoltageTier tier;

    public MonsterRepellentBlock(VoltageTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonsterRepellentBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (MaintenanceHatchBlockEntity.problemFor(stack) == Maintenance.Problem.SCREWDRIVER && level.getBlockEntity(pos) instanceof MonsterRepellentBlockEntity be) {
            if (!level.isClientSide()) be.screwdriver(player);
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** Окна у отпугивателя нет (как в GT). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}
